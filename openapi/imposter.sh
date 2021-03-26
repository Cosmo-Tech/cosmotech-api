docker run --rm -ti -p 8080:8080 \
    -v $(pwd):/opt/imposter/config \
    outofcoffee/imposter-openapi
