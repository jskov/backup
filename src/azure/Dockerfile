FROM ubuntu:16.04

ADD *.asc /opt/
RUN gpg --import /opt/exported-secret-key.asc
RUN gpg --import-ownertrust /opt/exported-owner-trust.asc
