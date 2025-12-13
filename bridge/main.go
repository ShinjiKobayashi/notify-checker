package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"

	_ "github.com/mattn/go-sqlite3"

	"maunium.net/go/mautrix"
	"maunium.net/go/mautrix/appservice"
	"maunium.net/go/mautrix/event"
	"maunium.net/go/mautrix/id"
)

var (
	matrixUser = os.Getenv("MATRIX_USER_ID")
	homeserver = os.Getenv("MATRIX_HOMESERVER_URL")
    domain     = os.Getenv("MATRIX_DOMAIN")
)

type NotificationEvent struct {
	PackageName string  `json:"packageName"`
	Title       string  `json:"title"`
	Text        string  `json:"text"`
	Tag         *string `json:"tag"`
}

type ReplyRequest struct {
	Title string `json:"title"`
	Text  string `json:"text"`
}

func main() {
	// 1. Setup Matrix AppService
	fs := flag.NewFlagSet("bridge", flag.ExitOnError)
    fs.Parse(os.Args[1:])

    loadTitleMap()

	as := appservice.Create()

    // Override config with environment variables if present
    if homeserver != "" {
        as.HomeserverDomain = domain
        // as.HomeserverURL is likely stored in config or used by client
        // Mautrix AppService struct has HomeserverDomain, but URL is in as.Config usually?
        // Actually appservice.Create() loads config into as.Config (if it exists, otherwise it might be separate).
        // Let's check if we can override via standard YAML structure loaded.
        // Assuming simple override:
    }

    // We need to ensure the AS uses the correct HS URL for client calls.
    // as.SetHomeserverURL(homeserver) is available based on doc.
    if homeserver != "" {
        as.SetHomeserverURL(homeserver)
    }

    as.Log.Info().Msg("AppService initialized")

	// 2. Setup Android Listener (HTTP)
    // Attach to existing AppService router
    as.Router.HandleFunc("/event", func(w http.ResponseWriter, r *http.Request) {
        if r.Method != http.MethodPost {
            http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
            return
        }
        body, _ := io.ReadAll(r.Body)
        var evt NotificationEvent
        if err := json.Unmarshal(body, &evt); err != nil {
            http.Error(w, "Bad Request", http.StatusBadRequest)
            return
        }

        if !strings.Contains(evt.PackageName, "teams") {
            as.Log.Debug().Str("package", evt.PackageName).Msg("Ignoring notification")
            return
        }

        handleAndroidNotification(as, evt)
        w.WriteHeader(http.StatusOK)
    })

	// 3. Matrix Event Handler
    // as.Events is a channel
    go func() {
        for evt := range as.Events {
            if evt.Type == event.EventMessage && evt.Sender == id.UserID(matrixUser) {
                handleMatrixMessage(as, evt)
            }
        }
    }()

    // Connect to Matrix
	as.Start()

    // Keep alive (Start is blocking? doc says func Start(), usually blocking for HTTP server)
    // Actually Start() starts the HTTP server.
    // If it's blocking, we don't need select{}. If it's not, we do.
    // Given the previous code had select{}, and standard http.Serve is blocking, but this is a wrapper.
    // Let's assume it blocks. Wait, usually Start() in frameworks is non-blocking or blocking depending on impl.
    // Looking at common mautrix bridges, they often block.
    // But to be safe, we will block if it returns.
    select {}
}

func handleAndroidNotification(as *appservice.AppService, n NotificationEvent) {
    ctx := context.TODO()

	// Logic:
	// 1. Determine Ghost User ID based on n.Title (Sender Name)
    // Use SHA256 of the title to ensure safe localpart for any language (Japanese, etc)
    hash := sha256.Sum256([]byte(n.Title))
    safeSenderHash := hex.EncodeToString(hash[:])[:12] // Short hash is enough for collision avoidance in single user context

    // Store mapping using the HASH as key
    titleMap[safeSenderHash] = n.Title
    saveTitleMap()

    ghostID := id.NewUserID(fmt.Sprintf("teams_%s", safeSenderHash), domain)
	intent := as.Intent(ghostID)

	// 2. Ensure Ghost User exists
	if err := intent.EnsureRegistered(ctx); err != nil {
		as.Log.Warn().Err(err).Str("ghost", ghostID.String()).Msg("Failed to register ghost")
	}
    intent.SetDisplayName(ctx, n.Title)

	// 3. Create or Find Room with Target User
    roomAlias := id.NewRoomAlias(fmt.Sprintf("teams_%s", safeSenderHash), domain)

    var roomID id.RoomID
    // Try to resolve alias
    resp, err := intent.ResolveAlias(ctx, roomAlias)
    if err == nil {
        roomID = resp.RoomID
    } else {
        // Create room
        createResp, err := intent.CreateRoom(ctx, &mautrix.ReqCreateRoom{
            Visibility: "private",
            RoomAliasName: fmt.Sprintf("teams_%s", safeSenderHash),
            Invite: []id.UserID{id.UserID(matrixUser)},
            Name: fmt.Sprintf("Teams - %s", n.Title),
            IsDirect: true,
        })
        if err != nil {
            as.Log.Error().Err(err).Msg("Failed to create room")
            return
        }
        roomID = createResp.RoomID
    }

	// 4. Send Message
	_, err = intent.SendMessageEvent(ctx, roomID, event.EventMessage, &event.MessageEventContent{
		MsgType: event.MsgText,
		Body:    n.Text,
	})
    if err != nil {
        as.Log.Error().Err(err).Msg("Failed to send message")
    }
}

func handleMatrixMessage(as *appservice.AppService, evt *event.Event) {
    ctx := context.TODO()

    members, err := as.BotIntent().JoinedMembers(ctx, evt.RoomID)
    if err != nil {
        as.Log.Error().Err(err).Msg("Failed to get members")
        return
    }

    var targetTitle string
    for userID := range members.Joined {
        strID := string(userID)
        if strings.Contains(strID, "@teams_") && strings.Contains(strID, domain) {
            parts := strings.Split(strID, ":")
            if len(parts) > 0 {
                localpart := parts[0] // @teams_name
                if len(localpart) > 7 { // remove @teams_
                    namePart := localpart[7:]
                    targetTitle = namePart
                }
            }
            break // Found the ghost
        }
    }

    if targetTitle == "" {
        return
    }

    realTitle, ok := titleMap[targetTitle]
    if !ok {
        as.Log.Warn().Str("target", targetTitle).Msg("Title map miss, falling back to reconstruction")
        realTitle = strings.ReplaceAll(targetTitle, "_", " ")
    }

    // Send to Android
    sendToAndroid(as, realTitle, evt.Content.AsMessage().Body)
}

var titleMap = make(map[string]string) // sanitized -> real
const mapFile = "title_map.json"

func loadTitleMap() {
    file, err := os.Open(mapFile)
    if err != nil {
        return // File likely doesn't exist yet
    }
    defer file.Close()
    json.NewDecoder(file).Decode(&titleMap)
}

func saveTitleMap() {
    file, err := os.Create(mapFile)
    if err != nil {
        fmt.Printf("Failed to save title map: %v\n", err)
        return
    }
    defer file.Close()
    json.NewEncoder(file).Encode(titleMap)
}

func sendToAndroid(as *appservice.AppService, title, text string) {
    req := ReplyRequest{Title: title, Text: text}
    data, _ := json.Marshal(req)

    resp, err := http.Post("http://redroid:8080/reply", "application/json", strings.NewReader(string(data)))
    if err != nil {
        as.Log.Error().Err(err).Msg("Failed to send to android")
        return
    }
    defer resp.Body.Close()
}
