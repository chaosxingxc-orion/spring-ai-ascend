# workmate-ui image — multi-stage: build the SPA, serve it with nginx and proxy /api to the API.
# Self-contained (no host build needed): docker build -t workmate-ui workmate-ui
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
