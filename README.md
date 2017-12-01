OSGI Trackers
================
OSGi framework performance and metadata trackers. This repository started as a fork project of the [eclipse-examples Github repo](https://github.com/evolanakis/eclipse-examples). It was modified in order to obtain specific tracking information of an OSGi framework, such as:

- **Performance data:** time taken by each bundle in the framework to change from an *INSTALLED* state to a *RESOLVED* state.
- **Classpath size:** number of classes per bundle (including dependencies). Own and releated classloaders are considered.
- **Resolving ordering:** order in which bundles are resolved in the framework.

