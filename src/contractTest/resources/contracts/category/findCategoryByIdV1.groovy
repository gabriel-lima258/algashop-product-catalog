package contracts.category

import org.springframework.cloud.contract.spec.Contract

Contract.make {

    request {
        method GET()
        headers {
            accept 'application/json'
        }
        url("/api/v1/categories/fffe6ec2-7103-48b3-8e4f-3b58e43fb75a")
    }

    response {
        status 200
        headers {
            contentType 'application/json'
        }
        body([
                id: fromRequest().path(3),
                name: 'Notebook',
                enabled: true
        ])
    }
}
