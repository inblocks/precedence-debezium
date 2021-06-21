[DRAFT]

# DEMO

For the purpose of the demo we will ask you to launch a bunch of containers. The [docker-compose](https://docs.docker.com/compose/) command will help you to do that by launching:
- `init` to initialize the system ([alpine](https://hub.docker.com/_/alpine) running `init.sh` script)
- `mysql` representing your database ([debezium/example-mysql:0.10](https://hub.docker.com/r/debezium/example-mysql))
- [Debezium](https://debezium.io/) containers
    - `zookeeper` [debezium/zookeeper:0.10](https://hub.docker.com/r/debezium/zookeeper)
    - `kafka` [wurstmeister/kafka:2.12-2.3.0](https://hub.docker.com/r/wurstmeister/kafka)
    - `debezium` [debezium/connect:0.10](https://hub.docker.com/r/debezium/connect)
- [**_precedence_**](https://precedence.inblocks.io/) containers
    - `redis` [redis:5.0.6-alpine](https://hub.docker.com/_/redis)
    - `precedence` [inblocks/precedence](https://hub.docker.com/r/inblocks/precedence) (REST API)
    - `precedence-debezium` [inblocks/precedence-debezium](https://hub.docker.com/r/inblocks/precedence-debezium) (this project)

## Step 1/3: deploy

```bash
docker-compose up -d
```

Wait for initialization:
```bash
docker logs -ft init
```

## Step 2/3: use it!

```bash
api="http://localhost:9000"

# create the first block (36 elements that were already in the MySQL database at launch)
curl -sS -XPOST "$api/blocks?pretty=true"

# create the second block, the number of element inserted in this block should be 0
curl -sS -XPOST "$api/blocks?pretty=true"
```

You have created the first 2 blocks in your blockchain.

If you modify something in your MySQL database, the change will be automatically captured and inserted into the **_precedence_** system. Start a MySQL client to make some changes.

```
docker exec -it mysql mysql -uroot -pdebezium inventory
INSERT INTO customers VALUES (default, "test", "test", "test");
DELETE FROM customers WHERE id=1005;
quit
```

You can compute a new block and check by yourself that the number of elements that have been inserted into this new block is the number of changes made on the MySQL database.

```bash
# create the third block, count should be 2
curl -sS -XPOST "$api/blocks?pretty=true"
```

You can retrieve the last version of the records pushed in the **_precedence_** system. The `previous` field contains the identifier of the last record of the chain at insertion time.

```bash
# get demo.inventory.customers.{"id":1005} corresponding to
# - "database.server.name": demo
# - database (from "database.whitelist"): inventory
# - table: customers
# - PK (JSON URL encoded): %7B%22id%22%3A1005%7D ({"id":1005})
curl -sS "$api/chains/demo.inventory.customers.%7B%22id%22%3A1005%7D?pretty=true"
```

The data isn't here because the last modification is a delete. We can retrieve the data on the previous record.

```bash
previous=$(curl -sS "$api/chains/demo.inventory.customers.%7B%22id%22%3A1005%7D" | sed -En 's/.*"previous":\["([^"]*).*/\1/p')
curl -sS "$api/records/$previous?data=true"
```

You can recreate some containers, wait for initialisation and verify by yourself that all is in the same state because we don't touch the containers which contains data.

```bash
docker rm -f init debezium precedence precedence-debezium
docker-compose up -d
# wait for initialisation
docker logs -ft init

# make a bloc
curl -sS -XPOST "$api/blocks?pretty=true"
# count should be 0 because there is nothing to do!
curl -sS "$api/chains/demo.inventory.customers.%7B%22id%22%3A1005%7D?pretty=true"
# still here!
```



## Step 3/3: clean

To clean out the demo you just need to shutdown the containers.

```bash
docker-compose down
```



# Tips
```bash
# connect you inside a container, precedence for example
docker exec -it precedence sh

# exec a precedence command
docker exec precedence precedence help

# list kafka topics
docker exec -it kafka bash -c '$KAFKA_HOME/bin/kafka-topics.sh --zookeeper zookeeper:2181 --list'

# watch Debezium kafka changes
docker exec kafka bash -c '$KAFKA_HOME/bin/kafka-console-consumer.sh \
    --bootstrap-server kafka:9093 \
    --property print.key=true \
    --property key.separator="         " \
    --whitelist "^demo\.inventory\..*" \
    --from-beginning'

# reset precedence-demo-inventory kafka offsets
application_id="precedence-demo-inventory"
whitelist='^demo\.inventory\..*'
docker exec -it kafka bash -c '$KAFKA_HOME/bin/kafka-streams-application-reset.sh \
    --application-id '"$application_id"' \
    --to-earliest \
    --input-topics $(echo $($KAFKA_HOME/bin/kafka-topics.sh \
        --zookeeper zookeeper:2181 \
        --list \
        | egrep "'"$whitelist"'") | tr "[:blank:]" ",")'
```
