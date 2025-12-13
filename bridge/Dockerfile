FROM golang:1.24-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN apk add --no-cache git build-base
RUN go mod download

COPY . .
RUN go build -o bridge .

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/bridge .
COPY config.yaml .
# registration.yaml should be mounted or generated

CMD ["./bridge"]
