# Influent

Influent is a library to implement a Fluentd's forward server on the JVM.

## Protocol

`influent.forward.ForwardServer` is almost compatible with [Forward Protocol Specification v1](https://github.com/fluent/fluentd/wiki/Forward-Protocol-Specification-v1).

This is the protocol for Fluentd's forward plugin.

* [forward Input Plugin](http://docs.fluentd.org/articles/in_forward)
* [forward Output Plugin](http://docs.fluentd.org/articles/out_forward)

Influent is a server implementation, so behaves as like `in_forward`.

Influent does not support these features now.

* handshake phase
* CompressedPackedForward mode

## Usage

### Dependency

#### Maven

```
<dependency>
    <groupId>com.okumin</groupId>
    <artifactId>influent-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

### How to use

Give `ForwardServer` the callback function that receives `EventStream`.
If you want to write `EventStreams` to stdout,

```java
// The callback function
ForwardCallback callback = ForwardCallback.ofSyncConsumer(
  stream -> System.out.println(stream),
  Executors.newFixedThreadPool(1)
);

// Constructs a new server
int port = 24224;
ForwardServer server = new ForwardServer
  .Builder(callback)
  .localAddress(port)
  .build();

// Starts the server on a new thread
server.start();

Thread.sleep(60 * 1000);

// ForwardServer#shutdown returns a CompletableFuture
CompletableFuture<Void> stopping = server.shutdown();
// The future will be completed when the server is terminated
stopping.get();
```

Execute the above code, and send a message by `fluent-cat` command.

```
$ echo '{"foo": "bar", "scores": [33, 4]}' | fluent-cat mofu
```

The received `EventStream` is written to stdout.

```
EventStream(Tag(mofu), [EventEntry(2016-11-13T13:10:59Z,{"foo":"bar","scores":[33,4]})])
```
