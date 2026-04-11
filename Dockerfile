FROM tomcat:10.1-jdk17-openjdk

# Remove default webapps to keep it clean
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy your WAR file (from your previous error) into the deployment folder
COPY am.war /usr/local/tomcat/webapps/am.war

EXPOSE 8080
CMD ["catalina.sh", "run"]