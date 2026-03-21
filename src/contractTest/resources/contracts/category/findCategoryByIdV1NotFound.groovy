package contracts.category

import org.springframework.cloud.contract.spec.Contract

Contract.make {

    request {
        method GET()
        headers {
            accept 'application/json'
        }
        url("/api/v1/categories/216e6ec2-7103-48b3-8e4f-3b58e43fb75a")
    }

    response {
        status 404
        headers {
            contentType 'application/problem+json'
        }
        body([
                instance: fromRequest().path(),
                type: "/errors/not-found",
                title: "Not found"
        ])
    }
}
