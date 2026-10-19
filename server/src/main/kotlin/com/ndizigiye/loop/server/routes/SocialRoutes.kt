package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.server.LoopServices
import com.ndizigiye.loop.server.plugins.JWT_AUTH
import com.ndizigiye.loop.server.plugins.userId
import com.ndizigiye.loop.server.plugins.viewerId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Comment threads, profiles and follows, and the inbox. */
fun Route.socialRoutes(services: LoopServices) {
    val comments = services.comments
    val users = services.users
    authenticate(JWT_AUTH, optional = true) {
        get("/api/comments/{id}/replies") {
            call.respond(comments.replies(call.idParam("id", COMMENT_NOT_FOUND), call.viewerId(), call.pageRequest()))
        }
        route("/api/users/{username}") {
            get {
                call.respond(users.byUsername(call.textParam("username"), call.viewerId()))
            }
            get("/videos") {
                call.respond(services.videos.userVideos(call.textParam("username"), call.viewerId(), call.pageRequest()))
            }
            get("/liked") {
                call.respond(services.videos.likedVideos(call.textParam("username"), call.viewerId(), call.pageRequest()))
            }
            get("/followers") {
                call.respond(users.followers(call.textParam("username"), call.viewerId(), call.pageRequest()))
            }
            get("/following") {
                call.respond(users.following(call.textParam("username"), call.viewerId(), call.pageRequest()))
            }
        }
    }
    authenticate(JWT_AUTH) {
        route("/api/comments/{id}") {
            delete {
                comments.delete(call.userId(), call.idParam("id", COMMENT_NOT_FOUND))
                call.respond(HttpStatusCode.NoContent)
            }
            put("/like") {
                call.respond(comments.setLiked(call.userId(), call.idParam("id", COMMENT_NOT_FOUND), liked = true))
            }
            delete("/like") {
                call.respond(comments.setLiked(call.userId(), call.idParam("id", COMMENT_NOT_FOUND), liked = false))
            }
        }
        route("/api/users/{username}/follow") {
            put {
                call.respond(users.setFollowing(call.userId(), call.textParam("username"), follow = true))
            }
            delete {
                call.respond(users.setFollowing(call.userId(), call.textParam("username"), follow = false))
            }
        }
        route("/api/notifications") {
            get {
                call.respond(services.notifications.list(call.userId(), call.pageRequest()))
            }
            get("/unread-count") {
                call.respond(services.notifications.unreadCount(call.userId()))
            }
            post("/read") {
                services.notifications.markRead(call.userId(), call.idQuery("upTo"))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
