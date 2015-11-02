# olib

A JMX client for riemann using clojure.async ie channels. The client borrows some ideas from riemann-jmx client, and currently it could work as a drop-in replacement for it. This compatibility might break later as other interesting options for managing its configuration are explored.  

A feature that distinguish it from riemann-jmx, and that was the main motivation for creating this project is using regex expressions to select MBean attributes from potentially large number of set of metrics (>1K)

The client is going under frequent changes coming out from testing it in a real environment. More detailed documentation should follow soon.

## Usage

FIXME

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
