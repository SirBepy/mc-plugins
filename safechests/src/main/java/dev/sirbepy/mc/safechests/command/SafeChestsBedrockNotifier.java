package dev.sirbepy.mc.safechests.command;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.safechests.SafeChestsConfig;
import dev.sirbepy.mc.safechests.bolt.BoltAccess;
import dev.sirbepy.mc.safechests.request.AccessRequest;
import dev.sirbepy.mc.safechests.request.RequestManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bedrock form UI for SafeChests' request/pending flow. Every answer, whether a chat click-event
 * or a form button, runs through {@link SafeChestsCommands#respondAsPlayer}, given here as
 * {@link AnswerHandler} so this class never duplicates that validation.
 */
final class SafeChestsBedrockNotifier {

    private final BoltAccess bolt;
    private final RequestManager requests;
    private final Supplier<SafeChestsConfig> config;
    private final BedrockForms bedrockForms;
    private final AnswerHandler answerHandler;

    /** The single validated answer path; see {@link SafeChestsCommands#respondAsPlayer}. */
    @FunctionalInterface
    interface AnswerHandler {
        void answer(Player owner, String id, SafeChestsCommands.Action action);
    }

    SafeChestsBedrockNotifier(BoltAccess bolt, RequestManager requests, Supplier<SafeChestsConfig> config,
                               BedrockForms bedrockForms, AnswerHandler answerHandler) {
        this.bolt = bolt;
        this.requests = requests;
        this.config = config;
        this.bedrockForms = bedrockForms;
        this.answerHandler = answerHandler;
    }

    /**
     * Java owners keep the clickable chat buttons. A Bedrock owner cannot invoke those, so they get
     * a typed-command instruction instead, plus (if configured) the answer form opens right away.
     * Package-private so tests can drive it directly.
     */
    void notifyOwnerOfRequest(Player owner, AccessRequest request) {
        SafeChestsConfig cfg = config.get();
        if (cfg.bedrockFormsEnabled() && bedrockForms.isBedrock(owner.getUniqueId())) {
            cfg.messages().send(owner, "request-prompt-bedrock", requestPlaceholders(request));
            if (cfg.bedrockAutoOpenRequestForm() && !requests.hasOpenForm(owner.getUniqueId())) {
                openAnswerForm(owner, request);
            }
            return;
        }
        sendRequestPrompt(owner, request);
    }

    private void sendRequestPrompt(Player owner, AccessRequest request) {
        config.get().messages().send(owner, "request-prompt", Map.of("id", request.id()), requestPlaceholders(request));
    }

    private TagResolver[] requestPlaceholders(AccessRequest request) {
        return new TagResolver[] {
                Placeholder.unparsed("requester", bolt.ownerDisplayName(request.requester())),
                Placeholder.unparsed("x", String.valueOf(request.x())),
                Placeholder.unparsed("y", String.valueOf(request.y())),
                Placeholder.unparsed("z", String.valueOf(request.z()))
        };
    }

    /**
     * Java owners get the pending requests re-listed as chat lines. A Bedrock owner gets the answer
     * form directly for a single pending request, or a chooser first for two or more. Package-private
     * so tests can drive it directly, without going through Brigadier.
     */
    void handlePending(Player owner) {
        SafeChestsConfig cfg = config.get();
        // Running the command is the owner's own reset: a closed auto-opened form (no callback ever
        // fires for a close, see RequestManager.clearFormOpen) would otherwise suppress auto-open forever.
        requests.clearFormOpen(owner.getUniqueId());
        List<AccessRequest> pendingRequests = requests.pendingForOwner(owner.getUniqueId());
        if (pendingRequests.isEmpty()) {
            cfg.messages().send(owner, "no-pending");
            return;
        }
        if (cfg.bedrockFormsEnabled() && bedrockForms.isBedrock(owner.getUniqueId())) {
            if (pendingRequests.size() == 1) {
                openAnswerForm(owner, pendingRequests.get(0));
            } else {
                openChooserForm(owner, pendingRequests);
            }
            return;
        }
        for (AccessRequest request : pendingRequests) {
            sendRequestPrompt(owner, request);
        }
    }

    /** One button per request; picking one opens that request's answer form. */
    private void openChooserForm(Player owner, List<AccessRequest> pendingRequests) {
        SafeChestsConfig cfg = config.get();
        List<String> buttons = new ArrayList<>();
        for (AccessRequest request : pendingRequests) {
            buttons.add(bolt.ownerDisplayName(request.requester()) + " at " + request.x() + " " + request.y() + " " + request.z());
        }
        bedrockForms.openButtons(owner, cfg.messages().renderPlain("form-request-title"), "", buttons, index -> {
            requests.clearFormOpen(owner.getUniqueId());
            if (index >= 0 && index < pendingRequests.size()) {
                openAnswerForm(owner, pendingRequests.get(index));
            }
        });
        requests.markFormOpen(owner.getUniqueId());
    }

    /** Allow once / 5 min / Always / Deny; a click answers through the exact same validated path
     * {@link SafeChestsCommands#respondAsPlayer} gives the chat buttons, by id, so an answered or
     * expired request in the meantime still falls through to the usual request-not-found message. */
    private void openAnswerForm(Player owner, AccessRequest request) {
        SafeChestsConfig cfg = config.get();
        List<String> buttons = List.of(
                cfg.messages().renderPlain("form-button-allow-once"),
                cfg.messages().renderPlain("form-button-five-min"),
                cfg.messages().renderPlain("form-button-always"),
                cfg.messages().renderPlain("form-button-deny"));
        String content = cfg.messages().renderPlain("form-request-content", requestPlaceholders(request));
        bedrockForms.openButtons(owner, cfg.messages().renderPlain("form-request-title"), content, buttons, index -> {
            // The only callback core's BedrockForms gives for this form; a close with no click never
            // reaches here, which is exactly the "stays suppressed until /safe-chests-pending" case.
            requests.clearFormOpen(owner.getUniqueId());
            SafeChestsCommands.Action action = switch (index) {
                case 0 -> SafeChestsCommands.Action.ALLOW_ONCE;
                case 1 -> SafeChestsCommands.Action.FIVE_MIN;
                case 2 -> SafeChestsCommands.Action.ALWAYS;
                default -> SafeChestsCommands.Action.DENY;
            };
            answerHandler.answer(owner, request.id(), action);
        });
        requests.markFormOpen(owner.getUniqueId());
    }
}
