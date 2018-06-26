#!/bin/bash -eux

# rationale: set a default DEPLOY_SECRETS
if [ -z "$JENKINS_DEPLOY" ]; then
  JENKINS_DEPLOY='no'
fi

if [ "$JENKINS_DEPLOY" == "yes" ]
then
  # Opcional, configura la aplicación descifrando secretos.
  rsaconfigcipher -P $JENKINS_SECRET_KEY src/main/resources/application.properties.rsa
fi

# Generate artifact
gradle assemble
# this generates build/libs/xtf2json-0.0.1-SNAPSHOT.jar
