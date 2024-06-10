#!/bin/bash

cd ../ && mvn clean deploy -Prelease -DaltDeploymentRepository=sonatype::https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/
