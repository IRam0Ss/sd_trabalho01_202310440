# E.D.E.N - Sistema de Comunicação Interna

![Hero Image do E.D.E.N](img/TelaBasica.png)

## Visão Geral

O projeto E.D.E.N é uma aplicação de chat distribuído desenvolvida em Java para comunicação interna em rede local. Ele foi concebido como uma solução híbrida de mensageria, unindo controle confiável via TCP, transferência rápida de mensagens via UDP e descoberta automática de servidor em broadcast.

A arquitetura foi pensada para suportar múltiplos usuários simultâneos, grupos de conversa, mensagens privadas, confirmação de recebimento/leitura e notificações em tempo real de presença e atualização de estado.

### Informações do projeto
- Autor: Iury Ramos Sodré
- Matrícula: 202310440
- Disciplina: Sistemas Distribuídos
- Semestre: 8º
- Data de início: 15/06/2026
- Última atualização: 14/09/2026

---

## Objetivo do sistema

O sistema tem como objetivo permitir que agentes ou usuários de uma mesma rede interna troquem mensagens em tempo real, organizando a comunicação em:

- grupos/channels de conversa
- mensagens privadas entre usuários
- listagem de agentes online
- atualização dinâmica de usuários conectados
- confirmação de entrega e leitura de mensagens
- detecção automática de servidor na rede local

---

## Arquitetura do sistema

### 1. Camada cliente
A camada cliente é responsável pela interação com o usuário e pelo envio e recebimento de dados na rede. Em termos estruturais, ela compreende:

- interface gráfica em JavaFX
- conexão TCP para controle
- socket UDP para mensagens em tempo real
- mecanismo de descoberta local do servidor
- processamento de confirmações e eventos de atualização

Principais classes:
- Cliente
- ClienteTCP
- ClienteUDP
- MessageListener
- ClienteGUI
- TutorialOverlay

### 2. Camada servidor
O servidor atua como ponto central de coordenação, autenticação e roteamento das mensagens. Ele mantém o estado dos usuários e grupos em memória e gerencia o fluxo de eventos.

Principais componentes:
- Servidor
- ServidorTCP
- ServidorUDP
- ServidorDiscovery
- GerenciadorGrupos
- AtendimentoCliente

### 3. Modelo de comunicação
O sistema aplica uma topologia híbrida:

- TCP: comandos de controle, autenticação, registro e consultas
- UDP: mensagens de conteúdo, notificações e atualização de presença
- Discovery UDP: busca automática do IP do servidor na rede local

Essa abordagem reduz a sobrecarga da conexão confiável para operações de controle e mantém a comunicação de conteúdo mais leve e rápida para mensagens instantâneas.

---

## Protocolo de aplicação

A comunicação entre cliente e servidor é encapsulada em classes de protocolo definidas em:

- Cliente/Protocol/APDU.java
- Cliente/utils/Protocolo.java
- Servidor/Protocol/APDU.java
- Servidor/utils/Protocolo.java

### Estrutura da APDU
A APDU representa uma unidade de dados da aplicação e encapsula campos como:

- operação
- nome do grupo
- nome do usuário
- texto da mensagem
- destinatário
- porta UDP do cliente
- identificador único da mensagem
- status de confirmação
- flag de visualização única

### Operações principais
Constantes do protocolo incluem:

- JOIN
- LEAVE
- LIST
- REGISTER
- USERS
- MEMBERS
- SEND
- SENDPVT
- SENDVU
- CONFIRM
- BLOCK
- UNBLOCK
- UPDATE_USERS
- SHUTDOWN

A classe Protocolo centraliza as constantes e portas padrão do sistema, incluindo:

- TCP padrão: 6789
- UDP padrão: 7777
- Discovery padrão: 8888
- Compatibilidade legado: 5000 e 5001

---

## Funcionamento do sistema

### Registro e autenticação
Ao iniciar, o cliente solicita ao usuário um nome e tenta conectar-se ao servidor usando TCP. Em seguida, envia uma APDU de REGISTER contendo:

- nome do usuário
- IP local do cliente
- porta UDP local para receber mensagens e notificações

O servidor valida o nome e registra o usuário em sua lista ativa.

### Participação em grupos
Ao executar JOIN em um grupo, o servidor:

1. cria o grupo se ele ainda não existir
2. adiciona o usuário à lista de membros
3. mantém as informações de atualização de IP e porta
4. responde ao cliente com confirmação OK ou ERRO

### Envio de mensagens
Mensagens em grupo ou privadas são enviadas por UDP ao servidor. O servidor:

- identifica o remetente
- valida se ele pertence ao grupo ou se a comunicação está permitida
- encaminha para destinatários apropriados
- aplica regras de bloqueio
- emite confirmações de status quando necessário

### Confirmações de entrega e leitura
O sistema implementa um mecanismo de confirmação por ticks com status:

- 1: mensagem recebida pelo servidor
- 2: mensagem entregue ao destinatário
- 3: mensagem lida pelo destinatário

Esse mecanismo é fundamental para simular confiabilidade mesmo em um transporte não orientado à conexão.

### Atualização de presença
Quando um usuário entra ou sai do sistema, ou quando há alteração de estado relevante, o servidor dispara notificações UDP para os clientes ativos, permitindo atualização dinâmica da interface e da lista de usuários online.

---

## Interoperabilidade e compatibilidade

O projeto foi concebido para operar em um ambiente local e para tolerar variações de configuração de rede. Isso fica evidente em duas decisões arquiteturais importantes:

### 1. Suporte a múltiplas portas
O sistema funciona em dois conjuntos de portas:

- padrão moderno: 6789 / 7777 / 8888
- legado: 5000 / 5001

Essa compatibilidade permite que o sistema interaja com versões anteriores ou ambientes com configuração específica de rede.

### 2. Descoberta automática de servidor
O serviço de discovery, implementado em ServidorDiscovery, responde a mensagens UDP simples para identificação do servidor na rede local.

Os padrões implementados incluem:

- SERVIDOR_IP → resposta: IP
- DISCOVER_EDEN → resposta: EDEN_HERE

Isso facilita a conexão automática do cliente sem que o usuário tenha que memorizar endereços fixos.

### 3. Interoperabilidade do protocolo
A interoperabilidade é boa dentro do ecossistema do próprio projeto, especialmente entre clientes e servidores Java que compartilham a mesma estrutura da APDU. Porém, o protocolo é altamente específico do sistema e depende de serialização Java, o que limita a interoperabilidade com implementações fora do projeto.

Em outras palavras:

- o sistema é interoperável entre versões internas do projeto
- o protocolo não é um padrão aberto universal
- ele não é diretamente compatível com clientes de terceiros sem reimplementação da APDU e das convenções de rede

---

## Estrutura do repositório

### Módulo Cliente
- Cliente/: aplicação do cliente
- Cliente/model/: lógica de comunicação com o servidor
- Cliente/view/: telas, JavaFX e componentes visuais
- Cliente/utils/: constantes e utilitários do protocolo
- Cliente/exceptions/: exceções específicas do cliente

### Módulo Servidor
- Servidor/: aplicação do servidor
- Servidor/model/: serviços de rede e orquestração
- Servidor/controller/: gerenciamento de grupos e atendimento
- Servidor/utils/: constantes e utilitários do protocolo
- Servidor/exceptions/: exceções específicas do servidor

### Recursos auxiliares
- img/: imagens e assets visuais
- scratch/: testes e experimentos de integração

---

## Requisitos de execução

### Ambiente
- Java JDK 8 ou superior
- JavaFX configurado no ambiente de desenvolvimento
- rede local com acesso entre cliente e servidor

### Execução sugerida
Como o projeto não utiliza Maven ou Gradle, a execução é normalmente feita via IDE como VS Code, IntelliJ ou Eclipse.

### 1. Iniciar o servidor
A classe principal do servidor é:

- Servidor/Principal.java

### 2. Iniciar o cliente
A classe principal do cliente é:

- Cliente/Principal.java

### 3. Conectar ao ambiente
No cliente, o usuário deve informar:

- IP do servidor
- nome de usuário

O cliente terá o servidor detectado automaticamente se estiver na mesma rede local e se o discovery estiver ativo.

---

## Principais pontos fortes

- arquitetura clara e bem separada por responsabilidades
- uso adequado de TCP para controle e UDP para mensagens rápidas
- suporte a grupos, mensagens privadas e atualização de presença
- mecanismo de confirmação por ticks e rastreamento de mensagens
- descoberta automática do servidor
- compatibilidade com portas padrões e legadas

---

## Limitações e observações técnicas

- o sistema depende de serialização Java, o que reduz interoperabilidade com outros idiomas e plataformas
- o estado de grupos e usuários é mantido em memória, sem persistência
- a correção de falhas e sincronização depende de regras de aplicação bem definidas
- a descoberta é simples e depende de rede local com broadcast habilitado
- não há camada de persistência ou autenticação avançada em banco de dados

Essas limitações não invalidam o projeto como exercício acadêmico de sistemas distribuídos, mas indicam claramente que ele foi implementado como solução local e funcional, e não como serviço empresarial de larga escala.

---

## Conclusão

O E.D.E.N é um sistema funcional de chat distribuído em Java, com arquitetura híbrida, protocolos próprios e boa organização modular. Ele demonstra bem os conceitos de comunicação em redes locais, multiplexação de canais, concorrência, grupos e sincronização distribuída em nível de aplicação.

O projeto se destaca pela lógica de projeto e pela integração entre cliente e servidor, além de apresentar um bom nível de sofisticação para um ambiente acadêmico. Sua maior limitação está na interoperabilidade externa, visto que ele depende de convenções internas de serialização e do próprio protocolo de aplicação.

---

## Licença e uso acadêmico

Este projeto foi desenvolvido como parte da disciplina de Sistemas Distribuídos e tem caráter acadêmico e educacional.

Desenvolvido por Iury Ramos Sodré.
