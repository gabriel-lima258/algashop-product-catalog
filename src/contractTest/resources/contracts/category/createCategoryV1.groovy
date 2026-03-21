package contracts.category

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    request {
        method POST()
        headers {
            accept 'application/json'
            contentType 'application/json'
        }
        urlPath("/api/v1/categories") {
            body([
                    name: value(
                            test("Notebook"),
                            stub(nonBlank())
                    ),
                    enabled: value(
                            test(true),
                            stub(anyBoolean())
                    )
            ])
        }
    }
    response {
        status 201
        headers {
            contentType 'application/json'
        }
        body([
                id: anyUuid(),
                name: fromRequest().body('$.name'),
                enabled: fromRequest().body('$.enabled')
        ])
    }
}