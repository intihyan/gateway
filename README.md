# gateway.resource.address.pipe

[![Build Status][build-status-image]][build-status]

[build-status-image]: https://travis-ci.org/kaazing/gateway.resource.address.pipe.svg?branch=develop
[build-status]: https://travis-ci.org/kaazing/gateway.resource.address.pipe

# About this Project

The gateway.resource.address.pipe is an implementation of pipe enpoint representations. It builds on the core abstraction provided by the gaeway.resource.address and defines endpoints for pipe URI schemes. Pipe based endpoints use in-memory transport to communicate with another endpoint in the same gateway.

# Building this Project

## Minimum requirements for building the project
* Java SE Development Kit (JDK) 7 or higher

## Steps for building this project
0. Clone the repo
0. mvn clean install

# Running this Project

0. Integrate this component in gateway.distribution by updating the version in gateway.distribution's pom
0. Build the corresponding gateway.distribution and use it for application development
