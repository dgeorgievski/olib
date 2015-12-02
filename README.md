# olib

A riemann-jmx client in Clojure using channels. Olib has been inspired by Two Sigma's [riemann-jmx-clj](https://github.com/twosigma/riemann-jmx), and was created to address a challenge of monitoring potentially huge number (thousands) of metrics with similar names.

Olib is currently a drop-in replacement for riemann-jmx-clj. In addition to the usual yaml settings, Olib recognizes attribs defined as regular expressions, which allows it to monitor all JMX metrics that match specific regex.

Whether compatibility would be preserved in the future is not clear yet. I am playing with the idea to replace yaml files with [Clojure EDN config files](https://clojure.github.io/clojure/clojure.edn-api.html), which would allow easier management of settings from Clojure's perspective, and more importantly enable easier automated generation of Config files. Yamls with tab indentations are a bit tricky to manage.

A new config format, should be able to eliminate repetition of some of the information (riemann and jmx sections), which might be important when it comes to running the agent with multiple configuration files.

## Building
Use `lein uberjar` to build the standalone jar. You can [download leiningen here](http://leiningen.org).

## Usage

Pass each of the riemann-jmx-config.yaml as command line options, e.g.:

```
java -jar olib-0.1.0-SNAPSHOT-standalone.jar jvm-config-1.yaml jvm-config-2.yaml jvm-config-3.yaml
```

The client disttiguishes between attr and aregex attributes,and it creates a dedicated async thread for each of these groups for each config file provided. This means if a config file contains both attr and aregex sections, the agent will create two threads, one for each of them, and establish two connections with the Riemann server.

## aregex attributes
This is a new feature that comes with Olib. One could provide a Clojure regex expression to match a potentially large set of attributes.

The following example shows a set of metrics related to HBase Region servers that match particular regex patterns.
```
queries :
-   service     : "hbase.region.regions"
    obj         : "Hadoop:service=HBase,name=RegionServer,sub=Regions"
    aregex      :
    -   (?i)Namespace.*_metric_storeCount
    -   (?i)Namespace.*_metric_storeFileCount
    -   (?i)Namespace.*_metric_memStoreSize
    -   (?i)Namespace.*_metric_storeFileSize
    -   (?i)Namespace.*_metric_compactionsCompletedCount
    -   (?i)Namespace.*_metric_numBytesCompactedCount
    -   (?i)Namespace.*_metric_numFilesCompactedCount
    -   (?i)Namespace.*_metric_get_num_ops
    -   (?i)Namespace.*_metric_mutateCount
    -   (?i)Namespace.*_metric_appendCount
    -   (?i)Namespace.*_metric_deleteCount
    -   (?i)Namespace.*_metric_incrementCount

```

More examples should be provided soon.

Olib supports composite mbeans as well.

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
