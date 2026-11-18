# Volume Remoto

Controle o volume da central multimídia Android (ex.: H-Tech) pelo navegador do celular.

Um app pequeno roda **na central** e abre uma página de controle na porta 8080.
O celular, conectado no mesmo Wi-Fi da central, abre essa página e aumenta, diminui ou silencia o volume.

## Como usar

1. **Baixe o APK**: aba **Releases** do GitHub → `VolumeRemoto.apk`
   (também fica em **Actions** → última execução → *Artifacts*).
2. **Instale na central**: copie o APK para um pendrive, coloque na central e abra o arquivo
   pelo gerenciador de arquivos. Se aparecer, permita "instalar de fontes desconhecidas".
3. **Abra o app "Volume Remoto" na central.** Ele mostra um endereço, por exemplo
   `http://192.168.43.1:8080`.
4. **No celular**, conectado ao Wi-Fi da central, abra esse endereço no Chrome.
   Dica: menu ⋮ → "Adicionar à tela inicial" para ficar como um app.

O servidor continua rodando em segundo plano e liga sozinho quando a central inicia.

## Se o volume não mudar

- Na página, troque **"Qual volume controlar"** (Mídia, Chamada etc.) e veja na lista de baixo qual número muda.
- Marque **"Imitar o botão físico de volume"**.
- Em algumas centrais o volume do Bluetooth é controlado por um chip separado (MCU).
  Nesse caso o Android não consegue mudar esse volume, e é preciso usar o botão físico ou a tela da central.

## API (para automações)

- `GET /api/status`: volumes atuais em JSON
- `GET /api/volume?stream=music&action=up|down|mute|max`
- `GET /api/volume?stream=music&action=set&value=10`
- Parâmetros opcionais: `mode=key` (imita o botão físico) e `steps=N`
