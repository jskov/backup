FROM ubuntu:18.04

RUN apt update && apt install gnupg -y

ADD *.asc /opt/
RUN gpg --import /opt/exported-secret-key.asc
RUN gpg --import-ownertrust /opt/exported-owner-trust.asc
