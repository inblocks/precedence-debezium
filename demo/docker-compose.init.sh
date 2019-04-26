#!/usr/bin/env sh

apk add --no-cache curl > /dev/null

while true; do
    curl -sSi "debezium:8083/connectors/" 2>/dev/null > /tmp/connectors.response
#    cat /tmp/topics.response
#    echo
    [[ $(grep -c "\[\"inventory-connector\"\]" /tmp/connectors.response) -eq 1 ]] && break
    curl -sS -w '\n' "debezium:8083/connectors/" -H "Content-Type:application/json" -d @/config.json 2>/dev/null 1>&2
    echo "Waiting Debezium connector..."
    sleep 1
done

while true; do
    docker exec -it kafka bash -c '$KAFKA_HOME/bin/kafka-topics.sh --zookeeper zookeeper:2181 --list' > /tmp/topics.response
#    cat /tmp/topics.response
#    echo
    [[ $(grep -c "dbserver1\.inventory" /tmp/topics.response) -eq 8 ]] && break
    echo "Waiting Debezium Kafka topics..."
    sleep 1
done

# we don't implement auto-reload topic for now :-(
docker restart precedence-debezium > /dev/null

while true; do
    docker exec precedence precedence chains get dbserver1.inventory.products_on_hand.%7B%22product_id%22%3A109%7D > /tmp/keys.response
#    cat /tmp/keys.response
#    echo
    [[ $(cat /tmp/keys.response | grep -c '"status": 200') -eq 1 ]] && break
    echo "Waiting precedence..."
    sleep 1
done

echo "Initialisation done!"

docker stop init
