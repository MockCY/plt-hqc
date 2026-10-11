// Prepare an immutable release locally; copy the resulting directory to FIRMWARE_ROOT on the server.
import { mkdir, readFile, writeFile, rename, stat } from 'node:fs/promises'
import { resolve, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { createHash, randomUUID } from 'node:crypto'

export async function publishFirmware({ binary, version, model = 'AVS-80A5', notes = '', root = 'data/firmware' }) {
  if (!/^(0|[1-9]\d{0,5})\.(0|[1-9]\d{0,5})\.(0|[1-9]\d{0,5})$/.test(version || '')) throw new Error('版本须为 x.y.z')
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$/.test(model) || notes.length > 500) throw new Error('型号或更新说明不正确')
  const bytes = await readFile(resolve(binary))
  if (bytes.length < 256 || bytes.length > 0x1e0000 || bytes[0] !== 0xe9 || bytes.readUInt16LE(12) !== 9) {
    throw new Error('请选择 ESP32-S3 应用固件 .ino.bin，大小不能超过 1,966,080 字节；不能使用 merged.bin')
  }
  if (!bytes.includes(Buffer.from(`ARVELLO_OTA|${model}|${version}|\0`))) throw new Error('固件内的型号或版本与发布参数不一致，请先修改源码版本并重新编译')
  const releaseId = `${model}-${version.replaceAll('.', '_')}`
  const directory = join(resolve(root), 'releases', releaseId)
  try { await stat(directory); throw new Error('版本已经发布，请使用新版本号') }
  catch (error) { if (error.code !== 'ENOENT') throw error }
  const temporary = `${directory}.tmp-${randomUUID()}`
  const release = { releaseId, model, chip: 'esp32s3', version, size: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex'), notes }
  await mkdir(temporary, { recursive: true })
  // Write the same bytes that were validated and hashed, even if the source is rebuilt concurrently.
  await writeFile(join(temporary, 'firmware.bin'), bytes)
  await writeFile(join(temporary, 'manifest.json'), JSON.stringify(release, null, 2) + '\n')
  await rename(temporary, directory)
  const latest = join(resolve(root), 'latest')
  await mkdir(latest, { recursive: true })
  const pointer = join(latest, `${model}.txt`), pending = `${pointer}.tmp-${randomUUID()}`
  await writeFile(pending, releaseId + '\n')
  await rename(pending, pointer)
  return release
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const args = Object.fromEntries(process.argv.slice(2).map(value => { const i = value.indexOf('='); return [value.slice(0, i), value.slice(i + 1)] }))
  publishFirmware(args).then(value => console.log(JSON.stringify(value, null, 2))).catch(error => { console.error(error.message); process.exitCode = 1 })
}
