#!/bin/sh
for i in 1 2 3
do
    gnome-terminal -- java -cp "jgroups-3.6.20.Final.jar":. -Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1 ServiceInterfaceImplementation 
done