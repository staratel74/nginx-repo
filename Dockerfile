# Используем базовый образ Ubuntu
FROM ubuntu:latest

# Обновляем пакетный менеджер и устанавливаем Nginx
RUN apt-get update && apt-get install -y nginx && apt-get install mc -y

# Удаляем дефолтную страницу Nginx и создаем свою
# RUN echo 'Hello Jenny! Hello Sun!' > /var/www/html/index.html
COPY index.html /var/www/html/index.html


# Открываем порт 80 для доступа к веб-серверу
EXPOSE 80

# Запускаем Nginx в форграунд режиме
CMD ["nginx", "-g", "daemon off;"]
