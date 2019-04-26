[DRAFT]



# What's this software for?

This repository contains the data processing software to convert and push the data stored using the [Debezium](https://debezium.io/) connector to the [inBlocks **_precedence_**](https://precedence.inblocks.io/) [REST API](https://precedence.inblocks.io/doc/v1/).

[demo](./demo)



# Run it!

## From Docker

```bash
docker pull inblocks/precedence-debezium
docker run --rm \
    -e APPLICATION_ID=precedence-dbserver1-inventory \
    -e BOOTSTRAP_SERVERS=kafka:9093 \
    -e INPUT_TOPIC_PATTERN='dbserver1\.inventory\..*' \
    -e PRECEDENCE_API=http://precedence \
    inblocks/precedence-debezium
```

## From sources

```bash
# Maven build
mvn package

export APPLICATION_ID=precedence-dbserver1-inventory
export BOOTSTRAP_SERVERS=kafka:9093
export INPUT_TOPIC_PATTERN='dbserver1\.inventory\..*'
export PRECEDENCE_API=http://localhost:9000

java -cp ./target/debezium.jar io.inblocks.precedence.Debezium
```



# Ongoing developments

- reload topic list every minutes
- **_precedence_** REST API authentication
- **_precedence_** Java client (dedicated project)
