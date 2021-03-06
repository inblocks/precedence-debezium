[DRAFT]



# What's this software for?

This repository contains the data processing software to convert and push the data stored using the [Debezium](https://debezium.io/)
connector to the [inBlocks **_precedence_**](https://precedence.inblocks.io/) [REST API](https://precedence.inblocks.io/doc/v1/).

[demo](./demo)



# Run it!

## From Docker

```bash
docker pull inblocks/precedence-debezium
docker run --rm \
    --network host \
    -e PRECEDENCE_API=http://localhost:9000 \
    -e PRECEDENCE_APPLICATION_ID=precedence-demo-inventory \
    -e PRECEDENCE_BOOTSTRAP_SERVERS=localhost:9093 \
    -e PRECEDENCE_INPUT_TOPIC_PATTERN=^demo\.inventory\..* \
    -e PRECEDENCE_STORE=true \
    inblocks/precedence-debezium
```

## From sources

```bash
# Maven build
mvn package

export PRECEDENCE_API=http://localhost:9000
export PRECEDENCE_APPLICATION_ID=precedence-demo-inventory
export PRECEDENCE_BOOTSTRAP_SERVERS=localhost:9093
export PRECEDENCE_INPUT_TOPIC_PATTERN=^demo\.inventory\..*
export PRECEDENCE_STORE=true

java -cp ./target/debezium.jar io.inblocks.precedence.Debezium
```



# Ongoing developments

- **_inBlocks precedence_** REST API authentication (SaaS)
- **_precedence_** Java client (dedicated project)
