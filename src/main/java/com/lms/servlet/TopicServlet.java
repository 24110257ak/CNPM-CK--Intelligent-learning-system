package com.lms.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lms.dao.TopicDAO;
import com.lms.dao.UserDAO;
import com.lms.model.Topic;
import com.lms.model.User;
import com.lms.util.JsonHelper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Controller cung cấp danh sách chủ đề học tập, chi tiết chủ đề
 * và quản lý phân quyền (chỉ người tạo môn hoặc Admin mới có quyền xóa/sửa).
 */
@WebServlet(name = "TopicServlet", urlPatterns = {"/api/topics", "/api/topics/*"})
public class TopicServlet extends HttpServlet {

    private final TopicDAO topicDAO = new TopicDAO();
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String pathInfo = req.getPathInfo();

        if (pathInfo == null || "/".equals(pathInfo) || "/list".equals(pathInfo)) {
            handleListTopics(req, resp);
        } else {
            handleGetTopicDetail(req, pathInfo.substring(1), resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(JsonHelper.error("Vui lòng đăng nhập để thêm môn học / chủ đề mới."));
            return;
        }

        JsonObject body = JsonHelper.parseRequestBody(req);
        if (body == null || !body.has("topicName") || body.get("topicName").getAsString().trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Tên chủ đề không được để trống."));
            return;
        }

        String topicName = body.get("topicName").getAsString().trim();
        String description = body.has("description") && !body.get("description").isJsonNull()
                ? body.get("description").getAsString().trim() : "";

        Integer createdBy = currentUser.getUserId();
        Topic created = topicDAO.findOrCreate(topicName, description, createdBy);

        JsonObject data = new JsonObject();
        data.addProperty("topicId", created.getTopicId());
        data.addProperty("topicName", created.getTopicName());
        data.addProperty("description", created.getDescription());
        data.addProperty("displayOrder", created.getDisplayOrder());
        data.addProperty("createdBy", created.getCreatedBy());
        data.addProperty("creatorName", currentUser.getFullName() != null ? currentUser.getFullName() : currentUser.getUsername());
        data.addProperty("canEdit", true);
        data.addProperty("canDelete", true);

        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().write(JsonHelper.success("Chủ đề đã sẵn sàng", data));
    }

    private void handleListTopics(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User currentUser = getAuthenticatedUser(req);
        boolean isAdmin = currentUser != null && "admin".equalsIgnoreCase(currentUser.getRole());
        List<Topic> topics = topicDAO.findAll();
        JsonArray array = new JsonArray();

        for (Topic t : topics) {
            JsonObject obj = new JsonObject();
            obj.addProperty("topicId", t.getTopicId());
            obj.addProperty("topicName", t.getTopicName());
            obj.addProperty("description", t.getDescription());
            obj.addProperty("parentTopicId", t.getParentTopicId());
            obj.addProperty("displayOrder", t.getDisplayOrder());
            obj.addProperty("questionCount", topicDAO.countQuestions(t.getTopicId()));
            obj.addProperty("createdBy", t.getCreatedBy());
            obj.addProperty("creatorName", t.getCreatorName() != null ? t.getCreatorName() : "Hệ thống");

            boolean isOwner = currentUser != null && t.getCreatedBy() != null && t.getCreatedBy().equals(currentUser.getUserId());
            obj.addProperty("canEdit", isAdmin || isOwner);
            obj.addProperty("canDelete", isAdmin || isOwner);
            array.add(obj);
        }

        resp.getWriter().write(JsonHelper.success("Lấy danh sách chủ đề thành công", array));
    }

    private void handleGetTopicDetail(HttpServletRequest req, String topicIdStr, HttpServletResponse resp) throws IOException {
        try {
            int topicId = Integer.parseInt(topicIdStr);
            Optional<Topic> topicOpt = topicDAO.findById(topicId);

            if (topicOpt.isPresent()) {
                Topic t = topicOpt.get();
                User currentUser = getAuthenticatedUser(req);
                boolean isAdmin = currentUser != null && "admin".equalsIgnoreCase(currentUser.getRole());
                boolean isOwner = currentUser != null && t.getCreatedBy() != null && t.getCreatedBy().equals(currentUser.getUserId());

                JsonObject obj = new JsonObject();
                obj.addProperty("topicId", t.getTopicId());
                obj.addProperty("topicName", t.getTopicName());
                obj.addProperty("description", t.getDescription());
                obj.addProperty("parentTopicId", t.getParentTopicId());
                obj.addProperty("displayOrder", t.getDisplayOrder());
                obj.addProperty("questionCount", topicDAO.countQuestions(t.getTopicId()));
                obj.addProperty("createdBy", t.getCreatedBy());
                obj.addProperty("creatorName", t.getCreatorName() != null ? t.getCreatorName() : "Hệ thống");
                obj.addProperty("canEdit", isAdmin || isOwner);
                obj.addProperty("canDelete", isAdmin || isOwner);

                resp.getWriter().write(JsonHelper.success("Chi tiết chủ đề", obj));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonHelper.error("Không tìm thấy chủ đề với ID: " + topicId));
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("ID chủ đề không hợp lệ."));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(JsonHelper.error("Vui lòng đăng nhập để cập nhật môn học."));
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Thiếu ID chủ đề cần cập nhật."));
            return;
        }

        try {
            int topicId = Integer.parseInt(pathInfo.substring(1));
            Optional<Topic> opt = topicDAO.findById(topicId);
            if (opt.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonHelper.error("Không tìm thấy chủ đề ID: " + topicId));
                return;
            }

            Topic topic = opt.get();
            boolean isAdmin = "admin".equalsIgnoreCase(currentUser.getRole());
            boolean isOwner = topic.getCreatedBy() != null && topic.getCreatedBy().equals(currentUser.getUserId());

            if (!isAdmin && !isOwner) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                String creatorMsg = topic.getCreatorName() != null ? topic.getCreatorName() : "giảng viên đã tạo";
                resp.getWriter().write(JsonHelper.error("Từ chối quyền: Bạn không thể sửa môn học này. Chỉ " + creatorMsg + " (người tạo) hoặc Quản trị viên mới có quyền sửa."));
                return;
            }

            JsonObject body = JsonHelper.parseRequestBody(req);
            if (body == null || !body.has("topicName") || body.get("topicName").getAsString().trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(JsonHelper.error("Tên chủ đề không được để trống."));
                return;
            }

            topic.setTopicName(body.get("topicName").getAsString().trim());
            if (body.has("description")) {
                topic.setDescription(body.get("description").isJsonNull() ? "" : body.get("description").getAsString().trim());
            }
            if (body.has("displayOrder") && !body.get("displayOrder").isJsonNull()) {
                topic.setDisplayOrder(body.get("displayOrder").getAsInt());
            }

            boolean ok = topicDAO.update(topic);
            if (ok) {
                resp.getWriter().write(JsonHelper.success("Cập nhật thông tin chủ đề thành công", null));
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write(JsonHelper.error("Không thể cập nhật chủ đề vào cơ sở dữ liệu."));
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("ID chủ đề không hợp lệ."));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(JsonHelper.error("Vui lòng đăng nhập để thực hiện xóa môn học."));
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("Thiếu ID chủ đề cần xóa."));
            return;
        }

        try {
            int topicId = Integer.parseInt(pathInfo.substring(1));
            Optional<Topic> opt = topicDAO.findById(topicId);
            if (opt.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(JsonHelper.error("Không tìm thấy chủ đề ID: " + topicId));
                return;
            }

            Topic topic = opt.get();
            boolean isAdmin = "admin".equalsIgnoreCase(currentUser.getRole());
            boolean isOwner = topic.getCreatedBy() != null && topic.getCreatedBy().equals(currentUser.getUserId());

            if (!isAdmin && !isOwner) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                String creatorMsg = topic.getCreatorName() != null ? topic.getCreatorName() : "giảng viên đã tạo";
                resp.getWriter().write(JsonHelper.error("Từ chối thao tác: Mọi người đều có thể dùng chung môn học này, nhưng chỉ " + creatorMsg + " (người tạo) mới có quyền xóa!"));
                return;
            }

            boolean ok = topicDAO.delete(topicId);
            if (ok) {
                resp.getWriter().write(JsonHelper.success("Đã xóa chủ đề và dọn dẹp các dữ liệu liên kết thành công", null));
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write(JsonHelper.error("Không thể xóa chủ đề khỏi cơ sở dữ liệu."));
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(JsonHelper.error("ID chủ đề không hợp lệ."));
        }
    }

    private User getAuthenticatedUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return (User) session.getAttribute("user");
        }
        String headerUserId = req.getHeader("X-User-Id");
        if (headerUserId != null && !headerUserId.trim().isEmpty()) {
            try {
                int uid = Integer.parseInt(headerUserId.trim());
                return userDAO.findById(uid).orElse(null);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
