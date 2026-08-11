package com.algaworks.algashop.product.catalog.application.storage;

// Porta de saida para armazenamento de arquivos.
//
// Ela mora em application/ e as implementacoes em infrastructure/ - a interface pertence
// a quem PRECISA dela, nao a quem a satisfaz. Por isso a aplicacao nao importa nada de
// infraestrutura, e trocar S3 por outro provedor nao toca uma linha de caso de uso.
//
// Repare no que NAO tem aqui: nenhum metodo recebe ou devolve bytes. O servico nunca
// carrega o arquivo. Ele so autoriza (requestUploadUrl), confere (fileExists) e apaga
// (deleteFile) - o trafego acontece entre o cliente e o provedor, direto.

import java.net.URL;

public interface StorageProvider {
    boolean healthCheck();

    // Devolve uma URL que autoriza um PUT com prazo de validade. Quem envia o arquivo
    // e o CLIENTE, e a URL ja carrega a assinatura - nao ha credencial no navegador.
    URL requestUploadUrl(FileReference fileReference);
    void deleteFile(String remoteFileName);
    boolean fileExists(String remoteFileName);
}
