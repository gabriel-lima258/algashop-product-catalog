package contracts.category

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    request {
        method PUT()
        headers {
            accept 'application/json'
            contentType 'application/json'
        }
        urlPath("/api/v1/categories/ccce6ec2-7103-48b3-8e4f-3b58e43fb75a") {
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
        status 200
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