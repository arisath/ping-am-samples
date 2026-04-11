FROM tomcat:11-jdk25

RUN apt-get update \
 && apt-get install -y curl \
 && rm -rf /var/lib/apt/lists/*

# Remove default webapps to keep it clean
RUN rm -rf /usr/local/tomcat/webapps/*



# Copy your WAR file (from your previous error) into the deployment folder
COPY am.war /usr/local/tomcat/webapps/am.war
COPY amster /usr/local/amster
COPY startup.sh /usr/local/


COPY opendj /usr/local/opendj


RUN chmod 775 /usr/local/*
RUN chmod 775 /usr/local/opendj/setup


EXPOSE 8080
CMD ["/usr/local/startup.sh", "run"]