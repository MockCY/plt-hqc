#!/bin/sh
set -eu

# Hex passwords can be inserted into JSON without shell/JSON escape ambiguity.
for password in "$ELASTIC_PASSWORD" "$KIBANA_PASSWORD" "$LOGSTASH_PASSWORD"; do
  case "$password" in
    ''|*[!0-9a-fA-F]*) echo 'ELK passwords must be hex strings (openssl rand -hex 32).' >&2; exit 1 ;;
  esac
  [ "${#password}" -ge 32 ] || { echo 'ELK passwords must be at least 32 characters.' >&2; exit 1; }
done

es() {
  curl --fail-with-body --silent --show-error \
    --user "elastic:$ELASTIC_PASSWORD" \
    --header 'Content-Type: application/json' "$@"
}

es -X POST http://elasticsearch:9200/_security/user/kibana_system/_password \
  -d "{\"password\":\"$KIBANA_PASSWORD\"}"
es -X PUT http://elasticsearch:9200/_security/role/arvello_logstash \
  -d '{"cluster":["monitor"],"indices":[{"names":["arvello-logs-*"],"privileges":["create_doc","auto_configure","view_index_metadata"]}]}'
es -X PUT http://elasticsearch:9200/_security/user/arvello_logstash \
  -d "{\"password\":\"$LOGSTASH_PASSWORD\",\"roles\":[\"arvello_logstash\"]}"
es -X PUT http://elasticsearch:9200/_ilm/policy/arvello-logs \
  -d @/setup/ilm-policy.json
es -X PUT http://elasticsearch:9200/_index_template/arvello-logs \
  -d @/setup/index-template.json

# A restart must not move the write alias back to the first index after rollover.
status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --user "elastic:$ELASTIC_PASSWORD" http://elasticsearch:9200/_alias/arvello-logs-write)
case "$status" in
  200) ;;
  404) es -X PUT http://elasticsearch:9200/arvello-logs-000001 \
    -d '{"aliases":{"arvello-logs-write":{"is_write_index":true}}}' ;;
  *) echo "Unable to check write alias: HTTP $status" >&2; exit 1 ;;
esac
