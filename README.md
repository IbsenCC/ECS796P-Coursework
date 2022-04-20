# ECS796P-Coursework

## Distributed matrix calculations

- allow matrices to be uploaded as files
- provide multiple instantiations of the gRPC server
- uses deadline scaling algorithm

## Install

```bash
mvn clean install
```

## Launch the server

```bash
mvn exec:java@server
```

## Launch the client

```bash
mvn exec:java@client
```
