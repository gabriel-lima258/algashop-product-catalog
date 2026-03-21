package contracts.category

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    request {
        method PUT()
        headers {
            accept 'application/json'
            contentType 'application/json'
        }
        urlPath("/api/v1/categories/c77e6ec2-7103-48b3-8e4f-3b58e43fb75a") {
            body([
                    name: value(
                            test("Notebook pro"),
                            stub(nonBlank())
                    ),
                    enabled: value(
                            test(false),
                            stub(anyBoolean())
                    )
            ])
        }
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