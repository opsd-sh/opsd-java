# Opsd Java library

`opsd` is the asynchronous Java client library for the Opsd API. It provides a
small, typed wrapper around the current public endpoints, including the
hello-world sandbox route and the user endpoints.

## Installation

```xml
<dependency>
  <groupId>sh.opsd</groupId>
  <artifactId>opsd</artifactId>
  <version>0.1.0</version>
</dependency>
```

The library requires Java 17 or later.

## Usage

The default client targets the production API at `https://api.opsd.sh/v1/`.
OAuth access tokens and API keys both use the HTTP Bearer scheme and are
redacted from object representations.

```java
import sh.opsd.ApiCredential;
import sh.opsd.HelloResponse;
import sh.opsd.OpsdClient;

ApiCredential credential = new ApiCredential("secret");
OpsdClient client = new OpsdClient(credential);
HelloResponse response = client.helloWorld().join();

System.out.println(response.message());
```

For local development, tests, or non-production deployments, supply an
`OpsdClientOptions` instance with a different base URI. Successful responses
are returned as immutable records. Non-success responses complete the future
exceptionally with `ApiException` when the server returns problem details, or
`UnexpectedResponseException` for an unrecognized response.

## Development

```console
./mvnw verify
./mvnw package
```

## License

Licensed under either the Apache License, Version 2.0 or the MIT license, at
your option.

