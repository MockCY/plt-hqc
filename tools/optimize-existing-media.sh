#!/bin/sh

set -eu

usage() {
    cat <<'EOF'
Usage: optimize-existing-media.sh [options]

Re-encode existing MP4 videos for reliable weak-network playback and aligned
audio/video timestamps. Originals are preserved under the run's backup folder.

Options:
  --media-root PATH   Media root (default: /data/arvello/media)
  --work-root PATH    Run output folder (default: MEDIA_ROOT/.maintenance/...)
  --paths-file FILE   Newline-separated paths relative to MEDIA_ROOT
  --only PATH         Process one path relative to MEDIA_ROOT
  --apply             Atomically replace each validated source; keep backup
  --force             Re-encode files that already match the target profile
  --help              Show this help
EOF
}

media_root=${MEDIA_ROOT:-/data/arvello/media}
run_id=${RUN_ID:-$(date -u +%Y%m%d-%H%M%S)}
work_root=
paths_file=
only_path=
apply=false
force=false

while [ "$#" -gt 0 ]; do
    case "$1" in
        --media-root)
            [ "$#" -ge 2 ] || { echo "Missing value for --media-root" >&2; exit 2; }
            media_root=$2
            shift 2
            ;;
        --work-root)
            [ "$#" -ge 2 ] || { echo "Missing value for --work-root" >&2; exit 2; }
            work_root=$2
            shift 2
            ;;
        --paths-file)
            [ "$#" -ge 2 ] || { echo "Missing value for --paths-file" >&2; exit 2; }
            paths_file=$2
            shift 2
            ;;
        --only)
            [ "$#" -ge 2 ] || { echo "Missing value for --only" >&2; exit 2; }
            only_path=$2
            shift 2
            ;;
        --apply)
            apply=true
            shift
            ;;
        --force)
            force=true
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

[ -d "$media_root" ] || { echo "Media root does not exist: $media_root" >&2; exit 1; }
command -v ffmpeg >/dev/null 2>&1 || { echo "ffmpeg is required" >&2; exit 1; }
command -v ffprobe >/dev/null 2>&1 || { echo "ffprobe is required" >&2; exit 1; }

if [ -z "$work_root" ]; then
    work_root="$media_root/.maintenance/video-optimization-$run_id"
fi

case "$work_root" in
    "$media_root"/.maintenance/*) ;;
    *) echo "Work root must be below $media_root/.maintenance" >&2; exit 1 ;;
esac

mkdir -p "$work_root/optimized" "$work_root/backup" "$work_root/logs" "$work_root/status"
input_list="$work_root/input.txt"
manifest="$work_root/manifest.tsv"
run_log="$work_root/run.log"
lock_dir="$media_root/.video-optimization.lock"

if ! mkdir "$lock_dir" 2>/dev/null; then
    echo "Another optimization run may be active: $lock_dir" >&2
    exit 1
fi

cleanup_lock() {
    rmdir "$lock_dir" 2>/dev/null || true
}
trap cleanup_lock EXIT HUP INT TERM

if [ -n "$only_path" ]; then
    printf '%s\n' "$only_path" > "$input_list"
elif [ -n "$paths_file" ]; then
    [ -f "$paths_file" ] || { echo "Paths file does not exist: $paths_file" >&2; exit 1; }
    sed 's/\r$//' "$paths_file" > "$input_list"
else
    find "$media_root/videos" -type f -name '*.mp4' ! -name '.*' -print \
        | sed "s#^$media_root/##" | sort > "$input_list"
fi

if [ ! -s "$manifest" ]; then
    printf 'status\tpath\tinput_bytes\toutput_bytes\tnote\n' > "$manifest"
fi

log() {
    printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$run_log"
}

run_ffmpeg() {
    if command -v nice >/dev/null 2>&1; then
        nice -n 10 ffmpeg "$@"
    else
        ffmpeg "$@"
    fi
}

number_or_zero() {
    case "$1" in
        ''|N/A|*[!0-9.\-]*) printf '0\n' ;;
        *) printf '%s\n' "$1" ;;
    esac
}

is_target_profile() {
    source_file=$1
    video_codec=$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of default=nw=1:nk=1 "$source_file" | head -n 1)
    width=$(number_or_zero "$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of default=nw=1:nk=1 "$source_file" | head -n 1)")
    height=$(number_or_zero "$(ffprobe -v error -select_streams v:0 -show_entries stream=height -of default=nw=1:nk=1 "$source_file" | head -n 1)")
    frame_rate=$(ffprobe -v error -select_streams v:0 -show_entries stream=avg_frame_rate -of default=nw=1:nk=1 "$source_file" | head -n 1)
    bit_rate=$(number_or_zero "$(ffprobe -v error -show_entries format=bit_rate -of default=nw=1:nk=1 "$source_file" | head -n 1)")
    video_start=$(number_or_zero "$(ffprobe -v error -select_streams v:0 -show_entries stream=start_time -of default=nw=1:nk=1 "$source_file" | head -n 1)")
    audio_start=$(number_or_zero "$(ffprobe -v error -select_streams a:0 -show_entries stream=start_time -of default=nw=1:nk=1 "$source_file" | head -n 1)")

    awk -v codec="$video_codec" -v width="$width" -v height="$height" \
        -v fps="$frame_rate" -v bitrate="$bit_rate" -v vs="$video_start" -v as="$audio_start" '
        function abs(value) { return value < 0 ? -value : value }
        BEGIN {
            split(fps, parts, "/")
            rate = parts[2] == 0 ? 0 : parts[1] / parts[2]
            ok = codec == "h264" && width <= 1280 && height <= 720 && rate <= 30 \
                && bitrate > 0 && bitrate <= 2200000 && abs(vs - as) <= 0.05
            exit(ok ? 0 : 1)
        }'
}

validate_output() {
    input_file=$1
    output_file=$2

    ffprobe -v error -select_streams v:0 -show_entries stream=codec_name \
        -of default=nw=1:nk=1 "$output_file" | grep -qx 'h264'

    input_duration=$(number_or_zero "$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$input_file" | head -n 1)")
    output_duration=$(number_or_zero "$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$output_file" | head -n 1)")
    awk -v input="$input_duration" -v output="$output_duration" '
        function abs(value) { return value < 0 ? -value : value }
        BEGIN {
            tolerance = input * 0.02
            if (tolerance < 0.5) tolerance = 0.5
            exit(input > 0 && output > 0 && abs(input - output) <= tolerance ? 0 : 1)
        }'

    run_ffmpeg -nostdin -hide_banner -loglevel error -xerror -i "$output_file" \
        -map 0:v:0 -map 0:a:0? -t 5 -f null - >/dev/null 2>&1
}

processed=0
skipped=0
failed=0

log "Run started: apply=$apply force=$force media_root=$media_root work_root=$work_root"

while IFS= read -r relative_path || [ -n "$relative_path" ]; do
    relative_path=$(printf '%s' "$relative_path" | sed 's/\r$//; s#^/api/media/files/##; s#^/##')
    [ -n "$relative_path" ] || continue
    case "$relative_path" in
        videos/*.mp4) ;;
        *)
            log "SKIP invalid path: $relative_path"
            printf 'skipped\t%s\t0\t0\tinvalid path\n' "$relative_path" >> "$manifest"
            skipped=$((skipped + 1))
            continue
            ;;
    esac
    case "/$relative_path/" in
        */../*|*/./*)
            log "SKIP unsafe path: $relative_path"
            printf 'skipped\t%s\t0\t0\tunsafe path\n' "$relative_path" >> "$manifest"
            skipped=$((skipped + 1))
            continue
            ;;
    esac

    source_file="$media_root/$relative_path"
    optimized_file="$work_root/optimized/$relative_path"
    partial_file="$optimized_file.partial.mp4"
    backup_file="$work_root/backup/$relative_path"
    ffmpeg_log="$work_root/logs/$(printf '%s' "$relative_path" | tr '/' '_').log"
    done_marker="$work_root/status/$(printf '%s' "$relative_path" | tr '/' '_').done"

    if [ -f "$done_marker" ]; then
        skipped=$((skipped + 1))
        continue
    fi
    if [ ! -f "$source_file" ]; then
        log "FAIL missing source: $relative_path"
        printf 'failed\t%s\t0\t0\tmissing source\n' "$relative_path" >> "$manifest"
        failed=$((failed + 1))
        continue
    fi
    input_bytes=$(wc -c < "$source_file" | tr -d ' ')

    if [ "$force" != true ] && is_target_profile "$source_file"; then
        log "SKIP already optimized: $relative_path"
        printf 'skipped\t%s\t%s\t%s\talready optimized\n' "$relative_path" "$input_bytes" "$input_bytes" >> "$manifest"
        : > "$done_marker"
        skipped=$((skipped + 1))
        continue
    fi

    mkdir -p "$(dirname "$optimized_file")" "$(dirname "$backup_file")"
    rm -f "$partial_file"
    has_audio=$(ffprobe -v error -select_streams a:0 -show_entries stream=index -of default=nw=1:nk=1 "$source_file" | head -n 1)
    log "PROCESS $relative_path ($input_bytes bytes)"

    if [ -n "$has_audio" ]; then
        if ! run_ffmpeg -nostdin -hide_banner -loglevel warning -y -fflags +genpts -i "$source_file" \
            -map 0:v:0 -map 0:a:0 \
            -vf "scale=w='min(1280,iw)':h='min(720,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,fps=24" \
            -c:v libx264 -preset veryfast -crf 25 -maxrate 1200k -bufsize 2400k \
            -pix_fmt yuv420p -profile:v main -level 3.1 -g 48 -keyint_min 48 -sc_threshold 0 \
            -c:a aac -b:a 96k -ar 44100 -af "aresample=async=1:first_pts=0" \
            -fps_mode cfr -avoid_negative_ts make_zero -movflags +faststart \
            "$partial_file" >"$ffmpeg_log" 2>&1; then
            log "FAIL ffmpeg: $relative_path (see $ffmpeg_log)"
            printf 'failed\t%s\t%s\t0\tffmpeg failed\n' "$relative_path" "$input_bytes" >> "$manifest"
            rm -f "$partial_file"
            failed=$((failed + 1))
            continue
        fi
    else
        if ! run_ffmpeg -nostdin -hide_banner -loglevel warning -y -fflags +genpts -i "$source_file" \
            -map 0:v:0 -an \
            -vf "scale=w='min(1280,iw)':h='min(720,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,fps=24" \
            -c:v libx264 -preset veryfast -crf 25 -maxrate 1200k -bufsize 2400k \
            -pix_fmt yuv420p -profile:v main -level 3.1 -g 48 -keyint_min 48 -sc_threshold 0 \
            -fps_mode cfr -avoid_negative_ts make_zero -movflags +faststart \
            "$partial_file" >"$ffmpeg_log" 2>&1; then
            log "FAIL ffmpeg: $relative_path (see $ffmpeg_log)"
            printf 'failed\t%s\t%s\t0\tffmpeg failed\n' "$relative_path" "$input_bytes" >> "$manifest"
            rm -f "$partial_file"
            failed=$((failed + 1))
            continue
        fi
    fi

    if ! validate_output "$source_file" "$partial_file"; then
        log "FAIL validation: $relative_path"
        printf 'failed\t%s\t%s\t0\tvalidation failed\n' "$relative_path" "$input_bytes" >> "$manifest"
        rm -f "$partial_file"
        failed=$((failed + 1))
        continue
    fi

    output_bytes=$(wc -c < "$partial_file" | tr -d ' ')
    mv "$partial_file" "$optimized_file"

    if [ "$apply" = true ]; then
        if [ -e "$backup_file" ]; then
            log "FAIL backup already exists: $relative_path"
            printf 'failed\t%s\t%s\t%s\tbackup already exists\n' "$relative_path" "$input_bytes" "$output_bytes" >> "$manifest"
            failed=$((failed + 1))
            continue
        fi
        mv "$source_file" "$backup_file"
        if mv "$optimized_file" "$source_file"; then
            chmod --reference="$backup_file" "$source_file" 2>/dev/null || true
        else
            mv "$backup_file" "$source_file"
            log "FAIL activation rolled back: $relative_path"
            printf 'failed\t%s\t%s\t%s\tactivation rolled back\n' "$relative_path" "$input_bytes" "$output_bytes" >> "$manifest"
            failed=$((failed + 1))
            continue
        fi
    fi

    : > "$done_marker"
    log "OK $relative_path ($input_bytes -> $output_bytes bytes, apply=$apply)"
    printf 'processed\t%s\t%s\t%s\tapply=%s\n' "$relative_path" "$input_bytes" "$output_bytes" "$apply" >> "$manifest"
    processed=$((processed + 1))
done < "$input_list"

log "Run finished: processed=$processed skipped=$skipped failed=$failed"
log "Manifest: $manifest"
log "Backups: $work_root/backup"

[ "$failed" -eq 0 ]
