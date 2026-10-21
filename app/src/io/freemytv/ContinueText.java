package io.freemytv;

/** Copy uses the project's existing Portuguese/English and translation dictionaries. */
final class ContinueText {
    private ContinueText() {}
    static String episode(ContinueModel.Item item) {
        StringBuilder out = new StringBuilder();
        if (!item.season.isEmpty()) out.append(Ui.tr("Temporada", "Season")).append(' ').append(item.season);
        if (!item.episodeNumber.isEmpty()) {
            if (out.length() > 0) out.append(" · ");
            out.append(Ui.tr("Episódio", "Episode")).append(' ').append(item.episodeNumber);
        }
        if (!item.episodeTitle.isEmpty()) {
            if (out.length() > 0) out.append(" · ");
            out.append(item.episodeTitle);
        }
        return out.toString();
    }
    static String title() { return Ui.tr("Continuar assistindo", "Continue watching"); }
    static String permission() { return Ui.tr("Permita acesso ao histórico publicado pelos aplicativos.", "Allow access to viewing history published by apps."); }
    static String action(ContinueWatching.State state) {
        return state == ContinueWatching.State.PERMISSION ? Ui.tr("Permitir acesso", "Allow access") : Ui.tr("Tentar novamente", "Try again");
    }
    static String status(ContinueWatching.State state) {
        switch (state) {
            case PERMISSION: return permission();
            case ERROR: return Ui.tr("Não foi possível ler o histórico de visualização.", "Viewing history could not be read.");
            case LOADING: return Ui.tr("Carregando histórico…", "Loading viewing history…");
            default: return Ui.tr("Comece a assistir em um aplicativo compatível", "Start watching in a compatible app");
        }
    }
}
