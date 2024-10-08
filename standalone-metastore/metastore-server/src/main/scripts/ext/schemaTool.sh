# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

THISSERVICE=schemaTool
export SERVICE_LIST="${SERVICE_LIST}${THISSERVICE} "

schemaTool() {
  METASTORE_OPTS=''
  CLASS=org.apache.hadoop.hive.metastore.tools.schematool.MetastoreSchemaTool
  if $cygwin; then
    METASTORE_LIB=`cygpath -w "$METASTORE_LIB"`
  fi
  JAR=${METASTORE_LIB}/hive-standalone-metastore-server-*.jar

  # hadoop 20 or newer - skip the aux_jars option and hiveconf
  exec $HADOOP jar $JAR $CLASS "$@"
}

schemaTool_help () {
  schemaTool -h
}
