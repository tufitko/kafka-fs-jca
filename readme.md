## Introduction

This Kafka extension module provides a mechanism to automatically reload the SSL engine when changes occur in your
keystore and truststore files. Additionally, it implements filesystem-based authentication. 
With this extension, there's no need to restart or reload the broker when these changes occur.


You can download JAR file from [Github release page](https://github.com/tufitko/kafka-fs-jca/releases/) and put it
into directory with kafka's jar files (for example: `/opt/kafka/libs/`), change server.properties and then restart broker.


## Features

Auto-Reload SSL Engine: Any changes in your keystore or truststore files will automatically trigger a reload of the SSL engine without needing a restart.

Filesystem Authentication: Authenticate users directly from a file which holds usernames and passwords.

## Configuration

### 1. Auto-Reload SSLEngine

To use the AutoReloadSSLEngineFactory, the following configurations can be added to your Kafka broker:

```
ssl.engine.factory.class=com.tufitko.kafkafsjca.AutoReloadSSLEngineFactory
tufitko.kafkafsjca.ssl_factory.monitor.refresh.interval.seconds=60
```

### 2. Filesystem Authentication

For the FilesystemAuthenticationLoginCallbackHandler and FilesystemLoginModule, the authentication file format should
be:

```makefile
username1:password1
username2:password2
```

You need to specify the path to this file in your JAAS configuration with the key path.

```
KafkaServer {
    com.tufitko.kafkafsjca.FilesystemLoginModule required
        username="admin" 
        password="admin"
        path="/etc/kafka/users.conf";
};
```

The following configurations should be added to your Kafka broker for specific listener:

```
listener.name.put_you_listener_name.plain.sasl.login.callback.handler.class=com.tufitko.kafkafsjca.FilesystemAuthenticationLoginCallbackHandler
listener.name.put_you_listener_name.plain.sasl.server.callback.handler.class=com.tufitko.kafkafsjca.FilesystemAuthenticationLoginCallbackHandler
```

## Important Classes
* `AutoReloadSSLEngineFactory`: Monitors keystore and truststore for changes and reloads the SSL engine as required.
* `FilesystemAuthenticationLoginCallbackHandler`: Handles authentication callbacks using a specified filesystem path.
* `FilesystemLoginModule`: JAAS login module for filesystem authentication.
