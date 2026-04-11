FROM tomcat:10.1-jdk17-openjdk

# Remove default webapps to keep it clean
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy your WAR file (from your previous error) into the deployment folder
COPY am.war /usr/local/tomcat/webapps/am.war
COPY amster /usr/local/amster
COPY startup.sh /usr/local/

RUN chmod 775 /usr/local/*

EXPOSE 8080
CMD ["/usr/local/startup.sh", "run"]