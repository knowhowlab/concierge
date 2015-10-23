#!/bin/bash
# start this script by a Hudson shell script:

# chmod u+x ./distribution/publish/publish.sh
# ./distribution/publish/publish.sh

set -x

version=`cat version.txt`
echo "VERSION=$version"
if [[ "$version" == *"SNAPSHOT"* ]] ; then
  BUILD_TYPE=snapshots
else
  BUILD_TYPE=releases
fi
echo "BUILD_TYPE=$BUILD_TYPE"
UPLOAD_LOCATION=/home/data/httpd/download.eclipse.org/concierge/$BUILD_TYPE
PUBLISH_LOG=$UPLOAD_LOCATION/publish.log
echo "UPLOAD_LOCATION=$UPLOAD_LOCATION"
echo "PUBLISH_LOG=$PUBLISH_LOG"

now=`date '+%Y/%m/%d %H:%M:%S'`
echo "$now: publishing last successful build" >>$PUBLISH_LOG

cp ./distribution/build/distributions/*.zip $UPLOAD_LOCATION
cp ./distribution/build/distributions/*.tar.gz $UPLOAD_LOCATION

now=`date '+%Y/%m/%d %H:%M:%S'`
echo "$now: finished publishing last successful build" >>$PUBLISH_LOG

# cleanup
rm /home/data/httpd/download.eclipse.org/concierge/concierge-incubation-5.0.0.SNAPSHOT-20151023130058.tar.gz
rm /home/data/httpd/download.eclipse.org/concierge/concierge-incubation-5.0.0.SNAPSHOT-20151023130058.zip
