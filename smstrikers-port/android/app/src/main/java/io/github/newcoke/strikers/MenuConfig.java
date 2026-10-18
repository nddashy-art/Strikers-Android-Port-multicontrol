package io.github.newcoke.strikers;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * As configurações.
 *
 * <p>UMA SEÇÃO DE CADA VEZ, com a lista de seções à esquerda. Antes eram todas
 * empilhadas numa rolagem só, e isso tem dois problemas numa tela deitada: o
 * conteúdo ocupa um terço da largura e sobra tela vazia ao lado, e quem quer
 * mexer na opacidade do botão tem de passar por resolução, suavização e filtro
 * até achar. Com a lista à esquerda, cada seção cabe inteira sem rolar.
 *
 * <p>O QUE APLICA NA HORA E O QUE NÃO: os ajustes do overlay são nossos, então
 * valem no mesmo instante. Os de vídeo o jogo lê uma vez, antes do main, e cada
 * seção que depende disso diz — um ajuste que parece não funcionar é pior do que
 * um que avisa que espera.
 */
final class MenuConfig {

    private MenuConfig() {}

    interface AoFechar { void fechou(); }

    // Durante a partida, el menú se dibuja dentro de la ventana SDL/Vulkan
    // en vez de abrir un segundo Window encima de la superficie de vídeo.
    private static Runnable cerrarMenuSuperpuesto;

    static void cerrar(Context ctx) {
        Runnable cerrar = cerrarMenuSuperpuesto;
        if (cerrar != null) cerrar.run();
    }

    // DE UM MÉTODO E NÃO DE UMA CONSTANTE: um array estático seria montado uma
    // vez por processo, no idioma que valia naquele instante, e continuaria em
    // memória depois de o idioma mudar. Os rótulos do trilho ficariam no idioma
    // antigo com o resto da tela no novo.
    private static String[] secoes(Context ctx) {
        String[] comuns = new String[] {
            ctx.getString(R.string.sec_controls),
            ctx.getString(R.string.sec_video),
            ctx.getString(R.string.sec_game),
            ctx.getString(R.string.sec_about),
        };
        // Only the in-game Activity has initialized SDL and loaded the native controller bridge.
        if (ctx instanceof StrikersActivity) {
            return new String[] {
                comuns[0], comuns[1], comuns[2], comuns[3],
                ctx.getString(R.string.sec_controllers),
            };
        }
        return comuns;
    }

    static void abrir(final Context ctx, final Preferencias prefs, final AoFechar aoFechar) {
        abrir(ctx, prefs, aoFechar, 0);
    }

    /** Abre una sección concreta; se usa para el atajo de mandos desde la pantalla inicial. */
    static void abrir(final Context ctx, final Preferencias prefs, final AoFechar aoFechar,
                      final int secaoInicial) {
        if (ctx instanceof StrikersActivity) {
            abrirSuperpuesto((StrikersActivity) ctx, prefs, aoFechar, secaoInicial);
            return;
        }
        // SUBCLASSE ANÔNIMA por causa do onWindowFocusChanged: esconder as
        // barras uma vez, antes de mostrar, não basta. O sistema traz as barras
        // de volta toda vez que a janela perde e recupera o foco -- e ela perde
        // o foco em cada diálogo de escolha que este menu abre. Sem reesconder,
        // a barra volta na primeira vez que alguém toca em "Resolução interna" e
        // fica lá.
        final Dialog dialogo = new Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar) {
            @Override
            public void onWindowFocusChanged(boolean temFoco) {
                super.onWindowFocusChanged(temFoco);
                if (temFoco) semBarras(getWindow());
            }
        };
        dialogo.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final LinearLayout trilho = new LinearLayout(ctx);
        trilho.setOrientation(LinearLayout.VERTICAL);
        trilho.setPadding(Vitrine.dp(ctx, 24), Vitrine.dp(ctx, 24),
                          Vitrine.dp(ctx, 8), Vitrine.dp(ctx, 24));

        final ScrollView rolagem = new ScrollView(ctx);
        rolagem.setPadding(Vitrine.dp(ctx, 8), Vitrine.dp(ctx, 24),
                           Vitrine.dp(ctx, 24), Vitrine.dp(ctx, 24));
        rolagem.setClipToPadding(false);

        final String[] secoes = secoes(ctx);
        final TextView[] abas = new TextView[secoes.length];
        final int[] atual = {Math.max(0, Math.min(secaoInicial, secoes.length - 1))};

        final Runnable pintar = () -> {
            for (int i = 0; i < abas.length; i++) {
                abas[i].setSelected(i == atual[0]);
                abas[i].setTextColor(i == atual[0] ? Vitrine.TEXTO : Vitrine.TEXTO_2);
                abas[i].invalidate();
            }
            rolagem.removeAllViews();
            rolagem.addView(conteudo(ctx, prefs, atual[0], dialogo));
            rolagem.scrollTo(0, 0);
        };

        TextView titulo = new TextView(ctx);
        titulo.setText(R.string.settings);
        titulo.setTextColor(Vitrine.TEXTO);
        titulo.setTextSize(22f);
        titulo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titulo.setPadding(Vitrine.dp(ctx, 14), 0, 0, Vitrine.dp(ctx, 12));
        trilho.addView(titulo);

        for (int i = 0; i < secoes.length; i++) {
            final int qual = i;
            abas[i] = aba(ctx, secoes[i]);
            abas[i].setOnClickListener(v -> {
                atual[0] = qual;
                pintar.run();
            });
            trilho.addView(abas[i]);
        }

        TextView fechar = Vitrine.botao(ctx, ctx.getString(R.string.done), Vitrine.AZUL, true);
        fechar.setOnClickListener(v -> dialogo.dismiss());
        LinearLayout.LayoutParams lpf = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpf.topMargin = Vitrine.dp(ctx, 20);
        trilho.addView(fechar, lpf);

        pintar.run();

        LinearLayout colunas = new LinearLayout(ctx);
        colunas.setOrientation(LinearLayout.HORIZONTAL);
        colunas.addView(trilho, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.28f));
        colunas.addView(rolagem, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.72f));

        FrameLayout pilha = new FrameLayout(ctx);
        // O FUNDO VAI ATÉ A BORDA e o conteúdo recua: a arte atrás do vidro
        // deve chegar ao canto da tela, e só o que se lê e se toca é que precisa
        // ficar longe da barra e do recorte da câmera.
        pilha.addView(Vitrine.fundo(ctx));
        pilha.addView(colunas, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        recuarDoSistema(colunas);
        dialogo.setContentView(pilha);

        Window w = dialogo.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            semBarras(w);
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        dialogo.setOnDismissListener(dlg -> {
            if (!Configuracao.gravar(ctx, prefs)) {
                Toast.makeText(ctx, R.string.ini_write_failed, Toast.LENGTH_LONG).show();
            }
            if (aoFechar != null) aoFechar.fechou();
        });
        dialogo.show();
    }

    /**
     * Menú dentro de la ventana SDL de la partida, sin crear otro Dialog/Window.
     */
    private static void abrirSuperpuesto(final StrikersActivity activity,
                                         final Preferencias prefs,
                                         final AoFechar aoFechar,
                                         final int secaoInicial) {
        final Context ctx = activity;
        final FrameLayout host = activity.findViewById(android.R.id.content);
        if (host == null) throw new IllegalStateException("No se encontró la vista raíz del juego");

        final FrameLayout[] raizMenu = new FrameLayout[1];
        final boolean[] finalizado = {false};
        final Runnable[] finalizarRef = new Runnable[1];
        final Runnable finalizar = () -> {
            if (finalizado[0]) return;
            finalizado[0] = true;
            if (raizMenu[0] != null && raizMenu[0].getParent() instanceof ViewGroup) {
                ((ViewGroup) raizMenu[0].getParent()).removeView(raizMenu[0]);
            }
            if (cerrarMenuSuperpuesto == finalizarRef[0]) cerrarMenuSuperpuesto = null;
            if (!Configuracao.gravar(ctx, prefs)) {
                Toast.makeText(ctx, R.string.ini_write_failed, Toast.LENGTH_LONG).show();
            }
            if (aoFechar != null) aoFechar.fechou();
        };
        finalizarRef[0] = finalizar;
        cerrarMenuSuperpuesto = finalizar;

        final LinearLayout trilho = new LinearLayout(ctx);
        trilho.setOrientation(LinearLayout.VERTICAL);
        trilho.setPadding(Vitrine.dp(ctx, 24), Vitrine.dp(ctx, 24),
                          Vitrine.dp(ctx, 8), Vitrine.dp(ctx, 24));

        final ScrollView rolagem = new ScrollView(ctx);
        rolagem.setPadding(Vitrine.dp(ctx, 8), Vitrine.dp(ctx, 24),
                           Vitrine.dp(ctx, 24), Vitrine.dp(ctx, 24));
        rolagem.setClipToPadding(false);

        final String[] secoes = secoes(ctx);
        final TextView[] abas = new TextView[secoes.length];
        final int[] atual = {Math.max(0, Math.min(secaoInicial, secoes.length - 1))};

        final Runnable pintar = () -> {
            for (int i = 0; i < abas.length; i++) {
                abas[i].setSelected(i == atual[0]);
                abas[i].setTextColor(i == atual[0] ? Vitrine.TEXTO : Vitrine.TEXTO_2);
                abas[i].invalidate();
            }
            rolagem.removeAllViews();
            rolagem.addView(conteudo(ctx, prefs, atual[0], null));
            rolagem.scrollTo(0, 0);
        };

        TextView titulo = new TextView(ctx);
        titulo.setText(R.string.settings);
        titulo.setTextColor(Vitrine.TEXTO);
        titulo.setTextSize(22f);
        titulo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titulo.setPadding(Vitrine.dp(ctx, 14), 0, 0, Vitrine.dp(ctx, 12));
        trilho.addView(titulo);

        for (int i = 0; i < secoes.length; i++) {
            final int qual = i;
            abas[i] = aba(ctx, secoes[i]);
            abas[i].setOnClickListener(v -> {
                atual[0] = qual;
                pintar.run();
            });
            trilho.addView(abas[i]);
        }

        TextView fechar = Vitrine.botao(ctx, ctx.getString(R.string.done), Vitrine.AZUL, true);
        fechar.setOnClickListener(v -> finalizar.run());
        LinearLayout.LayoutParams lpf = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpf.topMargin = Vitrine.dp(ctx, 20);
        trilho.addView(fechar, lpf);

        pintar.run();

        LinearLayout colunas = new LinearLayout(ctx);
        colunas.setOrientation(LinearLayout.HORIZONTAL);
        colunas.addView(trilho, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.28f));
        colunas.addView(rolagem, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.72f));

        FrameLayout pilha = new FrameLayout(ctx);
        pilha.setFocusableInTouchMode(true);
        pilha.setBackgroundColor(Color.BLACK);
        pilha.addView(Vitrine.fundo(ctx));
        pilha.addView(colunas, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        recuarDoSistema(colunas);

        raizMenu[0] = pilha;
        host.addView(pilha, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pilha.bringToFront();
        pilha.requestFocus();
    }

    /**
     * Esconde as barras do sistema na janela do diálogo.
     *
     * <p>ISTO ESTAVA SÓ NO RAMO ANTIGO. O código cobria Android 10 para trás e
     * não fazia nada de Android 11 em diante, que é onde este aparelho está —
     * então o diálogo abria de borda a borda com a barra de navegação por cima.
     * Deitado ela não fica embaixo: fica de pé na lateral direita, 135 pixels
     * em cima da coluna de opções.
     *
     * <p>O menu também abre por cima do jogo em tela cheia, e uma barra trazida
     * de volta aqui continua lá depois que ele fecha.
     */
    private static void semBarras(Window w) {
        if (w == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            w.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    /**
     * Recua a view do que o sistema estiver ocupando.
     *
     * <p>CINTO E SUSPENSÓRIO, e não redundância inútil. Com as barras escondidas
     * este recuo é zero, então em uso normal ele não faz nada. Ele existe para
     * os dois casos em que esconder não resolve: o recorte da câmera, que é
     * físico e não some — são 94 pixels na esquerda quando o aparelho está
     * deitado — e qualquer caminho em que a barra volte e fique.
     */
    private static void recuarDoSistema(final View v) {
        v.setOnApplyWindowInsetsListener((alvo, insets) -> {
            final int esq, cima, dir, baixo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                esq = i.left; cima = i.top; dir = i.right; baixo = i.bottom;
            } else {
                esq = insets.getSystemWindowInsetLeft();
                cima = insets.getSystemWindowInsetTop();
                dir = insets.getSystemWindowInsetRight();
                baixo = insets.getSystemWindowInsetBottom();
            }
            alvo.setPadding(esq, cima, dir, baixo);
            return insets;
        });
    }

    /** Uma entrada da lista de seções: pílula preenchida quando escolhida. */
    private static TextView aba(Context ctx, String rotulo) {
        TextView t = new TextView(ctx) {
            private final Paint tinta = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF caixa = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                if (isSelected()) {
                    caixa.set(0, 0, getWidth(), getHeight());
                    final float r = Vitrine.dp(getContext(), 11);
                    tinta.setColor(0x2EFFFFFF);
                    canvas.drawRoundRect(caixa, r, r, tinta);
                }
                super.onDraw(canvas);
            }
        };
        t.setText(rotulo);
        t.setTextSize(15f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(Vitrine.dp(ctx, 14), Vitrine.dp(ctx, 11),
                     Vitrine.dp(ctx, 14), Vitrine.dp(ctx, 11));
        t.setClickable(true);
        t.setWillNotDraw(false);
        return t;
    }

    // -----------------------------------------------------------------------
    // As seções
    // -----------------------------------------------------------------------

    private static View conteudo(Context ctx, Preferencias prefs, int secao, Dialog dialogo) {
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.VERTICAL);
        // MATCH_PARENT explícito, aqui e em cada painel abaixo. O padrão do
        // LinearLayout para addView(View) é WRAP_CONTENT, e um painel que se
        // encolhe ao conteúdo faz a linha de dentro -- rótulo com peso 1 mais o
        // valor à direita -- transbordar pela borda. Era isso que cortava os
        // números, e não o desenho do cursor.
        v.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        switch (secao) {
            case 0: controles(ctx, prefs, v); break;
            case 1: video(ctx, prefs, v); break;
            case 2: jogo(ctx, prefs, v); break;
            case 4: controladoresFisicos(ctx, v); break;
            default: sobre(ctx, prefs, v); break;
        }
        return v;
    }

    private static void controles(Context ctx, Preferencias prefs, LinearLayout v) {
        LinearLayout p = Vitrine.painel(ctx);
        p.addView(Vitrine.linhaLarga(ctx, ctx.getString(R.string.opacity), null,
                deslizante(ctx, prefs, Preferencias.OPACIDADE, 25, 100, "%")));
        p.addView(Vitrine.separador(ctx));
        p.addView(Vitrine.linhaLarga(ctx, ctx.getString(R.string.size), null,
                deslizante(ctx, prefs, Preferencias.TAMANHO, 75, 130, "%")));
        p.addView(Vitrine.separador(ctx));
        p.addView(Vitrine.linhaLarga(ctx, ctx.getString(R.string.deadzone),
                ctx.getString(R.string.deadzone_help),
                deslizante(ctx, prefs, Preferencias.ZONA_MORTA, 0, 35, "%")));
        largo(ctx, v, p);

        LinearLayout q = Vitrine.painel(ctx);
        q.addView(Vitrine.linha(ctx, ctx.getString(R.string.dpad), null,
                interruptor(ctx, prefs, Preferencias.DIRECIONAL)));
        q.addView(Vitrine.separador(ctx));
        q.addView(Vitrine.linha(ctx, ctx.getString(R.string.right_stick), null,
                interruptor(ctx, prefs, Preferencias.CSTICK)));
        margem(ctx, v, q);

        nota(ctx, v, ctx.getString(R.string.controls_note));
    }

    /**
     * Shows the gamepads SDL currently recognizes and lets the player assign each
     * one to player ports 1-4. This is intentionally separate from touch-overlay controls.
     */
    private static void controladoresFisicos(Context ctx, LinearLayout v) {
        TextView titulo = new TextView(ctx);
        titulo.setText(R.string.controllers_title);
        titulo.setTextColor(Vitrine.TEXTO);
        titulo.setTextSize(18f);
        titulo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titulo.setPadding(Vitrine.dp(ctx, 4), Vitrine.dp(ctx, 4),
                          Vitrine.dp(ctx, 4), Vitrine.dp(ctx, 8));
        v.addView(titulo);

        nota(ctx, v, ctx.getString(R.string.controllers_help));

        LinearLayout wifi = Vitrine.painel(ctx);
        TextView wifiTitulo = new TextView(ctx);
        wifiTitulo.setText(R.string.wifi_remote_title);
        wifiTitulo.setTextColor(Vitrine.TEXTO);
        wifiTitulo.setTextSize(15f);
        wifiTitulo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        wifiTitulo.setPadding(Vitrine.dp(ctx, 16), Vitrine.dp(ctx, 12),
                              Vitrine.dp(ctx, 16), Vitrine.dp(ctx, 4));
        wifi.addView(wifiTitulo);
        String[] info = ctx instanceof StrikersActivity
                ? ((StrikersActivity) ctx).informacionControlWifi()
                : new String[] {"No disponible", "------", "Detenido"};
        TextView wifiDatos = new TextView(ctx);
        wifiDatos.setText(ctx.getString(R.string.wifi_remote_address, info[0], ControladorWifiServer.PUERTO)
                + "\n" + ctx.getString(R.string.wifi_remote_pin, info[1])
                + "\n" + ctx.getString(R.string.wifi_remote_status, info[2]));
        wifiDatos.setTextColor(Vitrine.TEXTO_2);
        wifiDatos.setTextSize(13f);
        wifiDatos.setPadding(Vitrine.dp(ctx, 16), 0, Vitrine.dp(ctx, 16), Vitrine.dp(ctx, 12));
        wifi.addView(wifiDatos);
        largo(ctx, v, wifi);
        margem(ctx, v, wifi);
        nota(ctx, v, ctx.getString(R.string.wifi_remote_help));

        LinearLayout lista = new LinearLayout(ctx);
        lista.setOrientation(LinearLayout.VERTICAL);
        largo(ctx, v, lista);

        TextView actualizar = Vitrine.botao(ctx, ctx.getString(R.string.controllers_refresh),
                                            Vitrine.AZUL, true);
        actualizar.setOnClickListener(view -> lista.post(() -> llenarListaControladores(ctx, lista)));
        LinearLayout.LayoutParams lpActualizar = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpActualizar.topMargin = Vitrine.dp(ctx, 12);
        v.addView(actualizar, lpActualizar);

        // Consulta os dispositivos depois que a página está anexada à janela; não durante
        // a montagem do diálogo, quando a superfície do jogo ainda está trocando de foco.
        lista.post(() -> llenarListaControladores(ctx, lista));
    }

    private static void llenarListaControladores(Context ctx, LinearLayout lista) {
        lista.removeAllViews();
        final String[] filas;
        try {
            filas = StrikersActivity.nativeGetControllers();
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            android.util.Log.w("strikers", "No se pudieron consultar los mandos SDL", error);
            nota(ctx, lista, ctx.getString(R.string.controllers_none));
            return;
        }

        if (filas == null || filas.length == 0) {
            nota(ctx, lista, ctx.getString(R.string.controllers_none));
            return;
        }

        int mostrados = 0;
        for (String fila : filas) {
            if (fila == null) continue;
            String[] campos = fila.split("\\t", 3);
            if (campos.length != 3) continue;

            final int instancia;
            final int jugadorActual;
            try {
                instancia = Integer.parseInt(campos[0]);
                jugadorActual = Integer.parseInt(campos[1]);
            } catch (NumberFormatException error) {
                continue;
            }
            final String nombre = campos[2];

            LinearLayout tarjeta = Vitrine.painel(ctx);
            TextView nombreView = new TextView(ctx);
            nombreView.setText(nombre);
            nombreView.setTextColor(Vitrine.TEXTO);
            nombreView.setTextSize(15f);
            nombreView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            nombreView.setPadding(Vitrine.dp(ctx, 16), Vitrine.dp(ctx, 14),
                                  Vitrine.dp(ctx, 16), Vitrine.dp(ctx, 3));
            tarjeta.addView(nombreView);

            TextView estado = new TextView(ctx);
            estado.setText(jugadorActual > 0
                    ? ctx.getString(R.string.controller_current_player, jugadorActual)
                    : ctx.getString(R.string.controller_current_unassigned));
            estado.setTextColor(Vitrine.TEXTO_2);
            estado.setTextSize(12f);
            estado.setPadding(Vitrine.dp(ctx, 16), 0, Vitrine.dp(ctx, 16), Vitrine.dp(ctx, 8));
            tarjeta.addView(estado);

            LinearLayout fila12 = new LinearLayout(ctx);
            fila12.setOrientation(LinearLayout.HORIZONTAL);
            agregarBotonJugador(ctx, fila12, lista, instancia, jugadorActual, 1);
            agregarBotonJugador(ctx, fila12, lista, instancia, jugadorActual, 2);
            tarjeta.addView(fila12, parametrosFilaMandos(ctx));

            LinearLayout fila34 = new LinearLayout(ctx);
            fila34.setOrientation(LinearLayout.HORIZONTAL);
            agregarBotonJugador(ctx, fila34, lista, instancia, jugadorActual, 3);
            agregarBotonJugador(ctx, fila34, lista, instancia, jugadorActual, 4);

            TextView soltar = Vitrine.botao(ctx, ctx.getString(R.string.controller_unassign),
                    jugadorActual == 0 ? Vitrine.MATERIAL_ALT : Vitrine.AZUL,
                    jugadorActual == 0);
            soltar.setOnClickListener(view -> asignarControlador(ctx, lista, instancia, 0));
            LinearLayout.LayoutParams lpSoltar = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lpSoltar.setMargins(Vitrine.dp(ctx, 4), Vitrine.dp(ctx, 4),
                                Vitrine.dp(ctx, 4), Vitrine.dp(ctx, 4));
            fila34.addView(soltar, lpSoltar);
            tarjeta.addView(fila34, parametrosFilaMandos(ctx));

            LinearLayout.LayoutParams lpTarjeta = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpTarjeta.bottomMargin = Vitrine.dp(ctx, 12);
            lista.addView(tarjeta, lpTarjeta);
            mostrados++;
        }

        if (mostrados == 0) {
            nota(ctx, lista, ctx.getString(R.string.controllers_none));
        }
    }

    private static LinearLayout.LayoutParams parametrosFilaMandos(Context ctx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Vitrine.dp(ctx, 8);
        lp.rightMargin = Vitrine.dp(ctx, 8);
        return lp;
    }

    private static void agregarBotonJugador(Context ctx, LinearLayout fila, LinearLayout lista,
                                            int instancia, int jugadorActual, int jugador) {
        TextView boton = Vitrine.botao(ctx, ctx.getString(R.string.controller_player_button, jugador),
                jugadorActual == jugador ? Vitrine.AZUL : Vitrine.MATERIAL_ALT,
                jugadorActual == jugador);
        boton.setOnClickListener(view -> asignarControlador(ctx, lista, instancia, jugador));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Vitrine.dp(ctx, 4), Vitrine.dp(ctx, 4),
                      Vitrine.dp(ctx, 4), Vitrine.dp(ctx, 4));
        fila.addView(boton, lp);
    }

    private static void asignarControlador(Context ctx, LinearLayout lista,
                                           int instancia, int jugador) {
        final boolean correcto;
        try {
            correcto = StrikersActivity.nativeAssignController(instancia, jugador);
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            android.util.Log.w("strikers", "No se pudo asignar el mando SDL", error);
            Toast.makeText(ctx, R.string.controller_assign_failed, Toast.LENGTH_LONG).show();
            return;
        }

        if (!correcto) {
            Toast.makeText(ctx, R.string.controller_assign_failed, Toast.LENGTH_LONG).show();
            return;
        }
        if (jugador == 0) {
            Toast.makeText(ctx, R.string.controller_unassign_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(ctx, ctx.getString(R.string.controller_assign_success, jugador),
                           Toast.LENGTH_SHORT).show();
        }
        llenarListaControladores(ctx, lista);
    }

    private static void video(Context ctx, Preferencias prefs, LinearLayout v) {
        LinearLayout p = Vitrine.painel(ctx);
        // EM LINHAS, e não em porcentagem. O jogo guarda isto como um múltiplo
        // das 448 linhas do GameCube, e "200%" ali dá 896 — menos do que a tela
        // deste aparelho. Perguntar em linhas é perguntar a coisa que se sabe
        // querer; a conversão fica em Configuracao.
        p.addView(escolha(ctx, prefs, Preferencias.RES_LINHAS,
                ctx.getString(R.string.res_internal),
                new String[]{ctx.getString(R.string.res_follow),
                             ctx.getString(R.string.res_original),
                             "720", "1080", "1440", "2160"},
                new int[]{0, 448, 720, 1080, 1440, 2160},
                ctx.getString(R.string.res_help)));
        p.addView(Vitrine.separador(ctx));
        p.addView(escolha(ctx, prefs, Preferencias.FPS_LIMIT,
                ctx.getString(R.string.fps_limit),
                new String[]{ctx.getString(R.string.fps_unlimited), "30", "60", "120"},
                new int[]{0, 30, 60, 120},
                ctx.getString(R.string.fps_help)));
        p.addView(Vitrine.separador(ctx));
        p.addView(Vitrine.linha(ctx, ctx.getString(R.string.vsync),
                ctx.getString(R.string.vsync_help),
                interruptor(ctx, prefs, Preferencias.VSYNC)));
        largo(ctx, v, p);

        LinearLayout q = Vitrine.painel(ctx);
        // A FORMA DO QUADRO E O QUE FAZER COM A SOBRA. O celular deitado é bem
        // mais largo do que o 4:3 para o qual os menus foram desenhados; a
        // proporção decide se o jogo usa essa largura toda ou volta ao
        // enquadramento do console, e "fundo largo" decide o que acontece com a
        // arte quando ele usa.
        q.addView(escolha(ctx, prefs, Preferencias.PROPORCAO,
                ctx.getString(R.string.aspect),
                new String[]{ctx.getString(R.string.res_follow), "16:9", "4:3"},
                new int[]{0, 1, 2},
                ctx.getString(R.string.aspect_help)));
        q.addView(Vitrine.separador(ctx));
        q.addView(escolha(ctx, prefs, Preferencias.ESTICAR,
                ctx.getString(R.string.widen),
                new String[]{ctx.getString(R.string.widen_fit),
                             ctx.getString(R.string.widen_stretch)},
                new int[]{0, 1},
                ctx.getString(R.string.widen_help)));
        q.addView(Vitrine.separador(ctx));
        q.addView(escolha(ctx, prefs, Preferencias.MSAA, ctx.getString(R.string.msaa),
                new String[]{ctx.getString(R.string.off), "4x"}, new int[]{1, 4}, null));
        q.addView(Vitrine.separador(ctx));
        q.addView(escolha(ctx, prefs, Preferencias.ANISO, ctx.getString(R.string.aniso),
                new String[]{"1x", "2x", "4x", "8x", "16x"}, new int[]{1, 2, 4, 8, 16},
                ctx.getString(R.string.aniso_help)));
        margem(ctx, v, q);

        nota(ctx, v, ctx.getString(R.string.video_note));
    }

    private static void jogo(Context ctx, Preferencias prefs, LinearLayout v) {
        LinearLayout p = Vitrine.painel(ctx);
        p.addView(Vitrine.linha(ctx, ctx.getString(R.string.unlock_all),
                ctx.getString(R.string.unlock_all_help),
                interruptor(ctx, prefs, Preferencias.DESBLOQUEAR)));
        p.addView(Vitrine.separador(ctx));
        p.addView(Vitrine.linha(ctx, ctx.getString(R.string.hud),
                ctx.getString(R.string.hud_help),
                interruptor(ctx, prefs, Preferencias.HUD)));
        largo(ctx, v, p);
        nota(ctx, v, ctx.getString(R.string.game_note));
    }

    private static void sobre(Context ctx, Preferencias prefs, LinearLayout v) {
        LinearLayout p = Vitrine.painel(ctx);

        Drivers.Driver d = Drivers.porId(ctx, prefs.texto(Preferencias.DRIVER));
        p.addView(Vitrine.linha(ctx, ctx.getString(R.string.video_driver),
                d != null ? d.nome : ctx.getString(R.string.driver_system), null));
        p.addView(Vitrine.separador(ctx));
        p.addView(Vitrine.linha(ctx, ctx.getString(R.string.game_folder),
                StrikersHome.homeDir(ctx).getAbsolutePath(), null));
        largo(ctx, v, p);

        nota(ctx, v, ctx.getString(R.string.about_note));
    }

    // -----------------------------------------------------------------------

    /** Acrescenta ocupando a largura toda. Ver a nota em conteudo(). */
    private static void largo(Context ctx, LinearLayout pai, View filho) {
        pai.addView(filho, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void margem(Context ctx, LinearLayout pai, View filho) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Vitrine.dp(ctx, 12);
        pai.addView(filho, lp);
    }

    private static void nota(Context ctx, LinearLayout pai, String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(Vitrine.TEXTO_3);
        t.setTextSize(11f);
        t.setLineSpacing(Vitrine.dp(ctx, 2), 1f);
        t.setPadding(Vitrine.dp(ctx, 16), Vitrine.dp(ctx, 12), Vitrine.dp(ctx, 16), 0);
        pai.addView(t);
    }

    private static Interruptor interruptor(Context ctx, Preferencias prefs, String chave) {
        Interruptor i = new Interruptor(ctx, prefs.ligado(chave));
        i.aoMudar(v -> prefs.ligar(chave, v));
        return i;
    }

    private static Deslizante deslizante(Context ctx, Preferencias prefs, String chave,
                                         int min, int max, String sufixo) {
        Deslizante s = new Deslizante(ctx, min, max, prefs.numero(chave), sufixo);
        s.aoMudar(v -> prefs.definir(chave, v));
        return s;
    }

    /** Uma escolha entre valores fixos: o valor à direita, a lista num diálogo. */
    private static View escolha(Context ctx, Preferencias prefs, String chave, String rotulo,
                                String[] nomes, int[] valores, String ajuda) {
        TextView valor = new TextView(ctx);
        valor.setTextColor(Vitrine.TEXTO_2);
        valor.setTextSize(14f);
        valor.setText(nomeDe(nomes, valores, prefs.numero(chave)));

        LinearLayout linha = Vitrine.linha(ctx, rotulo, ajuda, valor);
        linha.setClickable(true);
        // COM A ESCOLHA ATUAL MARCADA. Era setItems, uma lista chapada onde nada
        // indicava o valor em vigor: para saber em que estava, era preciso
        // fechar a lista e ler o texto da linha. É assim que se toca no item
        // errado e se sai achando que nada mudou.
        linha.setOnClickListener(v -> new AlertDialog.Builder(
                    ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(rotulo)
                .setSingleChoiceItems(nomes, indiceDe(valores, prefs.numero(chave)),
                    (dlg, i) -> {
                        prefs.definir(chave, valores[i]);
                        valor.setText(nomes[i]);
                        dlg.dismiss();
                    })
                .show());
        return linha;
    }

    /** Onde o valor em vigor está na lista, ou -1 se não estiver nela. */
    private static int indiceDe(int[] valores, int valor) {
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] == valor) return i;
        }
        // -1 é o que o AlertDialog entende por "nenhum marcado", que é honesto
        // para um valor posto à mão no ini e que não está entre as opções.
        return -1;
    }

    private static String nomeDe(String[] nomes, int[] valores, int valor) {
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] == valor) return nomes[i];
        }
        // Um valor que veio do ini editado à mão e não está na lista: mostrado
        // como está, em vez de mentir dizendo que é o primeiro.
        return Integer.toString(valor);
    }
}
