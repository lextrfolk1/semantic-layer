FROM openpolicyagent/opa:0.68.0
WORKDIR /policies
COPY src/main/resources/opa /policies
EXPOSE 8181
CMD ["run", "--server", "--addr=0.0.0.0:8181", "/policies"]
