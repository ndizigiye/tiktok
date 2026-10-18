package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.api.CreateCommentRequest
import com.ndizigiye.loop.api.ViewEventRequest
import com.ndizigiye.loop.server.LoopServices
import com.ndizigiye.loop.server.media.UploadSpec
import com.ndizigiye.loop.server.media.receiveUpload
import com.ndizigiye.loop.server.plugins.JWT_AUTH
import com.ndizigiye.loop.server.plugins.userId
import com.ndizigiye.loop.server.plugins.viewerId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Feeds, videos and the comments under a video. */
fun Route.videoRoutes(services: LoopServices) {
    val videos = services.videos
    authenticate(JWT_AUTH, optional = true) {
        get("/api/feed/for-you") {
            call.respond(services.forYou.page(call.viewerId(), call.pageRequest()))
        }
        route("/api/videos/{id}") {
            get {
                call.respond(videos.get(call.videoId(), call.viewerId()))
            }
            post("/share") {
                call.respond(videos.share(call.videoId()))
            }
            post("/views") {
                videos.recordView(call.viewerId(), call.videoId(), call.receive<ViewEventRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
            get("/comments") {
                call.respond(services.comments.topLevel(call.videoId(), call.viewerId(), call.pageRequest()))
            }
        }
    }
    authenticate(JWT_AUTH) {
        get("/api/feed/following") {
            call.respond(videos.followingFeed(call.userId(), call.pageRequest()))
        }
        post("/api/videos") {
            val userId = call.userId()
            val upload = call.receiveUpload(UploadSpec.VIDEO, services.staging::newFile)
            call.respond(HttpStatusCode.Created, services.uploads.publishVideo(userId, upload))
        }
        route("/api/videos/{id}") {
            delete {
                services.uploads.deleteVideoMedia(videos.delete(call.userId(), call.videoId()))
                call.respond(HttpStatusCode.NoContent)
            }
            put("/like") {
                call.respond(videos.setLiked(call.userId(), call.videoId(), liked = true))
            }
            delete("/like") {
                call.respond(videos.setLiked(call.userId(), call.videoId(), liked = false))
            }
            put("/bookmark") {
                call.respond(videos.setBookmarked(call.userId(), call.videoId(), bookmarked = true))
            }
            delete("/bookmark") {
                call.respond(videos.setBookmarked(call.userId(), call.videoId(), bookmarked = false))
            }
            post("/not-interested") {
                videos.markNotInterested(call.userId(), call.videoId())
                call.respond(HttpStatusCode.NoContent)
            }
            post("/comments") {
                val request = call.receive<CreateCommentRequest>()
                call.respond(HttpStatusCode.Created, services.comments.create(call.userId(), call.videoId(), request))
            }
        }
    }
}

private fun ApplicationCall.videoId(): Long = idParam("id", VIDEO_NOT_FOUND)
