FROM tomcat:11-jdk25

# Combine package install and cleanup
RUN apt-get update && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/* /usr/local/tomcat/webapps/*

# 1. Copy individual scripts directly to /usr/local/
COPY --chmod=775 startup.sh ds-install.sh /usr/local/

# 2. Copy directories (this preserves the folder name at the destination)
COPY --chmod=775 amster /usr/local/amster/
COPY --chmod=775 amster-config /usr/local/amster-config/
COPY --chmod=775 opendj /usr/local/opendj/

# 3. Copy the WAR
COPY am.war /usr/local/tomcat/webapps/am.war

EXPOSE 8080
ENTRYPOINT ["/usr/local/startup.sh"]
CMD ["run"]