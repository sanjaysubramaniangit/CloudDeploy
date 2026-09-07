# ==============================================================================
# Stage 1: Build Frontend Assets with Node.js
# ==============================================================================
FROM node:18-alpine AS builder
WORKDIR /app

# Copy dependency specifications first for layer caching
COPY package.json package-lock.json* ./
RUN npm install

# Pass relative API base URL for containerized Nginx reverse proxy
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# Copy frontend source code and compile production distribution
COPY . .
RUN npm run build

# ==============================================================================
# Stage 2: Serve with Production Nginx
# ==============================================================================
FROM nginx:1.25-alpine AS runner

# Remove default Nginx welcome assets and configuration
RUN rm -rf /usr/share/nginx/html/* /etc/nginx/conf.d/default.conf

# Copy compiled static assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom reverse proxy and SPA routing configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose sole production HTTP ingress port
EXPOSE 80

# Health check using BusyBox wget included in Alpine Linux
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
