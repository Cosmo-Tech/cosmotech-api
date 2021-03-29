logger.info(context.request.method)
if (context.request.method == 'POST') {
    respond {
        withStatusCode 201
    }
}

usingDefaultBehaviour()
