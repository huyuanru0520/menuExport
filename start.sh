#!/bin/sh
RESOURCE_NAME=demo-0.0.1-SNAPSHOT.jar
 
# 先kill -15 pid
tpid=`ps -ef|grep $RESOURCE_NAME|grep -v grep|grep -v kill|awk '{print $2}'`
if [ ${tpid} ]; then
echo 'Stop Process...'
kill -15 $tpid
fi
sleep 5
# 再kill -9 pid
tpid=`ps -ef|grep $RESOURCE_NAME|grep -v grep|grep -v kill|awk '{print $2}'`
if [ ${tpid} ]; then
echo 'Kill Process!'
kill -9 $tpid
else
echo 'Stop Success!'
fi
# 启动app 
tpid=`ps -ef|grep $RESOURCE_NAME|grep -v grep|grep -v kill|awk '{print $2}'`
if [ ${tpid} ]; then
    echo 'App is running.'
else
    echo 'App is NOT running.'
fi
 
rm -f tpid
nohup java -jar $RESOURCE_NAME >nohup.out &
# nohup java -jar $RESOURCE_NAME > boot-example-hello.log &
echo $! > tpid
echo Start Success!

