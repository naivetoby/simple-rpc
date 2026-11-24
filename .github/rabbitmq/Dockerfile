# syntax=docker/dockerfile:1.6
FROM rabbitmq:4.1.0@sha256:d5172fdc63a9d0e8b31c57b4b473f73c19f47b75ef0cf2a49af06a86f8c0541d

ADD --checksum=sha256:567f876378e70af9d949de4066bbb2fc30162b46bcbc9efe2430076b172e4a87 https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v4.1.0/rabbitmq_delayed_message_exchange-4.1.0.ez /opt/rabbitmq/plugins/
RUN chmod 644 /opt/rabbitmq/plugins/rabbitmq_delayed_message_exchange-4.1.0.ez \
    && rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange \
    && rm -f /var/lib/rabbitmq/.erlang.cookie
