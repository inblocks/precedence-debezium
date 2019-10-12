#!/usr/bin/env sh

apk add --no-cache curl >/dev/null

while true; do
  curl -sSi "debezium:8083/connectors/" 2>/dev/null >/tmp/connectors.response
  # cat /tmp/topics.response
  # echo
  [ "$(grep -c "\[\"demo-inventory\"\]" /tmp/connectors.response)" -eq 1 ] && break
  curl -sS -w '\n' "debezium:8083/connectors/" -H "Content-Type:application/json" -d @/config.json 2>/dev/null 1>&2
  echo "Waiting Debezium connector..."
  sleep 1
done

while true; do
  curl -sS "precedence:9000/chains/demo.inventory.products_on_hand.%7B%22product_id%22%3A109%7D?pretty=true" >/tmp/keys.response
  # cat /tmp/keys.response
  # echo
  [ "$(grep -c '"status": 200' /tmp/keys.response)" -eq 1 ] && break
  echo "Waiting precedence..."
  sleep 1
done

echo "Initialization done!"
