package org.apache.ignite.ci.web.rest.board;

import com.google.inject.Injector;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.ignite.ci.user.ITcBotUserCreds;
import org.apache.ignite.ci.web.CtxListener;
import org.apache.ignite.tcbot.engine.board.BoardService;
import org.apache.ignite.tcbot.engine.ui.BoardSummaryUi;

@Path(BoardRestService.BOARD)
@Produces(MediaType.APPLICATION_JSON)
public class BoardRestService {
    public static final String BOARD = "board";

    /** Servlet Context. */
    @Context
    private ServletContext ctx;

    /** Current Request. */
    @Context
    private HttpServletRequest req;

    @GET
    @Path("summary")
    public BoardSummaryUi getSummary() {

        final ITcBotUserCreds creds = ITcBotUserCreds.get(req);
        final Injector injector = CtxListener.getInjector(ctx);
        final BoardService boardSvc = injector.getInstance(BoardService.class);

        return boardSvc.summary(creds);
    }
}
