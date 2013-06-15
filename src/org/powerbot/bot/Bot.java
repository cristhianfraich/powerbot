package org.powerbot.bot;

import org.powerbot.client.Client;
import org.powerbot.client.Constants;
import org.powerbot.event.EventMulticaster;
import org.powerbot.event.PaintEvent;
import org.powerbot.event.TextPaintEvent;
import org.powerbot.gui.BotChrome;
import org.powerbot.gui.component.BotPanel;
import org.powerbot.script.Script;
import org.powerbot.script.internal.InputHandler;
import org.powerbot.script.internal.MouseHandler;
import org.powerbot.script.internal.ScriptHandler;
import org.powerbot.script.lang.Stoppable;
import org.powerbot.script.methods.MethodContext;
import org.powerbot.script.util.Delay;
import org.powerbot.service.GameAccounts;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * @author Timer
 */
public final class Bot implements Runnable, Stoppable {//TODO re-write bot
	public static final Logger log = Logger.getLogger(Bot.class.getName());
	private MethodContext methodContext;
	public final Runnable callback;
	public final ThreadGroup threadGroup;
	private final PaintEvent paintEvent;
	private final TextPaintEvent textPaintEvent;
	private final EventMulticaster multicaster;
	private volatile RSLoader appletContainer;
	private volatile BotStub stub;
	private BufferedImage image;
	public AtomicBoolean refreshing;
	private Constants constants;
	private BotPanel panel;
	private GameAccounts.Account account;
	private BufferedImage backBuffer;
	private MouseHandler mouseHandler;
	private InputHandler inputHandler;
	private ScriptHandler scriptController;

	public Bot() {
		appletContainer = null;
		callback = null;
		stub = null;

		threadGroup = new ThreadGroup(Bot.class.getName() + "@" + Integer.toHexString(hashCode()));

		multicaster = new EventMulticaster();
		panel = null;

		account = null;

		final Dimension d = new Dimension(BotChrome.PANEL_WIDTH, BotChrome.PANEL_HEIGHT);
		image = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
		backBuffer = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
		paintEvent = new PaintEvent();
		textPaintEvent = new TextPaintEvent();

		new Thread(threadGroup, multicaster, multicaster.getClass().getName()).start();
		refreshing = new AtomicBoolean(false);

		methodContext = new MethodContext(this);
		scriptController = new ScriptHandler(methodContext, multicaster);
	}

	public void run() {
		start();
	}

	public void start() {
		log.info("Starting bot");
		appletContainer = new RSLoader();
		appletContainer.setCallback(new Runnable() {
			public void run() {
				setClient((Client) appletContainer.getClient());
				final Graphics graphics = image.getGraphics();
				appletContainer.update(graphics);
				graphics.dispose();
				resize(BotChrome.PANEL_WIDTH, BotChrome.PANEL_HEIGHT);
			}
		});

		if (!appletContainer.load()) {
			return;
		}
		stub = new BotStub(appletContainer, appletContainer.getClientLoader().crawler);
		appletContainer.setStub(stub);
		stub.setActive(true);
		log.info("Starting game");
		new Thread(threadGroup, appletContainer, "Loader").start();
		BotChrome.getInstance().panel.setBot(this);
	}

	@Override
	public boolean isStopping() {
		boolean stopping = false;
		return stopping;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void stop() {
		log.info("Unloading environment");
		for (final Stoppable module : new Stoppable[]{mouseHandler, scriptController, multicaster}) {
			if (module != null) {
				module.stop();
			}
		}
		new Thread(threadGroup, new Runnable() {
			@Override
			public void run() {
				terminateApplet();
			}
		}).start();
	}

	void terminateApplet() {
		if (stub != null) {
			log.fine("Terminating stub activities");
			stub.setActive(false);
		}
		if (appletContainer != null) {
			log.fine("Shutting down applet");
			appletContainer.stop();
			appletContainer.destroy();
			appletContainer = null;
			stub = null;
			this.methodContext.setClient(null);
		}
	}

	public void startScript(final Script script) {
		scriptController.start(script);
	}

	public void stopScripts() {
		synchronized (scriptController) {
			if (scriptController != null) {
				scriptController.stop();
				scriptController = null;
			}
		}
	}

	public BufferedImage getImage() {
		return image;
	}

	public BufferedImage getBuffer() {
		return backBuffer;
	}

	public RSLoader getAppletContainer() {
		return appletContainer;
	}

	public MethodContext getMethodContext() {
		return methodContext;
	}

	public Constants getConstants() {
		return constants;
	}

	public InputHandler getInputHandler() {
		return inputHandler;
	}

	public MouseHandler getMouseHandler() {
		return mouseHandler;
	}

	public void resize(final int width, final int height) {
		backBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		if (appletContainer != null) {
			appletContainer.setSize(width, height);
			final Graphics buffer = backBuffer.getGraphics();
			appletContainer.update(buffer);
			buffer.dispose();
		}
	}

	public Graphics getBufferGraphics() {
		final Graphics back = backBuffer.getGraphics();
		if (this.methodContext.getClient() != null && panel != null && !BotChrome.getInstance().isMinimised()) {
			paintEvent.graphics = back;
			textPaintEvent.graphics = back;
			textPaintEvent.id = 0;
			try {
				multicaster.fire(paintEvent);
				multicaster.fire(textPaintEvent);
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
		back.dispose();
		final Graphics imageGraphics = image.getGraphics();
		imageGraphics.drawImage(backBuffer, 0, 0, null);
		imageGraphics.dispose();
		if (panel != null) {
			panel.repaint();
		}
		return backBuffer.getGraphics();
	}

	public void setPanel(final BotPanel panel) {
		this.panel = panel;
	}

	private void setClient(final Client client) {
		this.methodContext.setClient(client);
		client.setCallback(new CallbackImpl(this));
		constants = new Constants(appletContainer.getTspec().constants);
		new Thread(threadGroup, new SafeMode(this)).start();
		mouseHandler = new MouseHandler(appletContainer, client);
		inputHandler = new InputHandler(appletContainer, client);
		new Thread(threadGroup, mouseHandler).start();
	}

	public Canvas getCanvas() {
		final Client client = methodContext.getClient();
		return client != null ? client.getCanvas() : null;
	}

	public EventMulticaster getEventMulticaster() {
		return multicaster;
	}

	public GameAccounts.Account getAccount() {
		return account;
	}

	public void setAccount(final GameAccounts.Account account) {
		this.account = account;
	}

	public ScriptHandler getScriptController() {
		return this.scriptController;
	}

	public synchronized void refresh() {
		if (refreshing.get()) {
			return;
		}

		refreshing.set(true);
		new Thread(threadGroup, new Runnable() {
			public void run() {
				log.info("Refreshing environment");
				final ScriptHandler container = getScriptController();
				if (container != null) {
					container.suspend();
				}

				terminateApplet();
				resize(BotChrome.PANEL_WIDTH, BotChrome.PANEL_HEIGHT);

				while (getMethodContext().getClient() == null || getMethodContext().game.getClientState() == -1) {
					Delay.sleep(1000);
				}
				if (container != null) {
					container.resume();
				}

				refreshing.set(false);
			}
		}).start();
	}

	private final class SafeMode implements Runnable {
		private final Bot bot;

		public SafeMode(final Bot bot) {
			this.bot = bot;
		}

		public void run() {
			if (bot != null && bot.methodContext.getClient() != null) {
				for (int i = 0; i < 30; i++) {
					if (!methodContext.keyboard.isReady()) {
						Delay.sleep(500, 1000);
					} else {
						break;
					}
				}
				if (methodContext.keyboard.isReady()) {
					methodContext.keyboard.send("s");
				}
			}
		}
	}
}
