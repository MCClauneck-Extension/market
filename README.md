# Market Extension for MCEconomy

**Path:** `plugins/MCEconomy/extensions/Market`

This extension adds a fully functional, GUI-based shop system to the **MCEconomy** ecosystem. It allows server administrators to create infinite market categories (shops) where players can buy and sell items using various currencies defined in the core MCEconomy plugin.

---

## 🛠 How It Works

The Market extension operates as a dynamic module loaded by the MCEconomy core. It bridges the gap between physical Minecraft items and digital economy currencies through an inventory interface.

### 1. Data Storage & Configuration
* **YAML-Based Storage:** Each market category (e.g., `ore`, `blocks`, `food`) is stored as a separate `.yml` file in the extension's folder.
* **Item Integrity:** Items are stored using **Base64 serialization**. This ensures that *every* detail of an item is preserved, including enchantments, custom names, lore, durability, and NBT tags.
* **Dynamic Loading:** Markets are loaded into memory when the server starts or when saved via the in-game editor, ensuring fast access without constant disk reading.

### 2. The Player Experience (Buying & Selling)
Players interact with the market through a paginated inventory GUI.
* **Buying (Left-Click):**
    1.  The system checks if the player has inventory space.
    2.  It performs an **asynchronous** withdrawal from the player's balance (to prevent server lag).
    3.  If successful, the item is delivered synchronously to the player's inventory.
* **Selling (Right-Click):**
    1.  The system verifies the player has the *exact* item (matching metadata) in their inventory.
    2.  The item is removed synchronously.
    3.  The payment is deposited **asynchronously** into the player's account.
* **Visual Feedback:** The GUI automatically appends pricing information and interaction hints to the item's lore, so players know exactly how much an item costs or pays out.

### 3. The In-Game Editor (Admin System)
Unlike traditional shop plugins that require tedious config file editing, this extension features a robust **In-Game GUI Editor**.
* **Drag-and-Drop Setup:** Admins can open an edit mode where they simply drag items from their inventory into the market GUI to add them.
* **Chat-Based Input:**
    * **Shift + Left Click** on an item triggers a chat prompt to set the **Buy Price**.
    * **Shift + Right Click** on an item triggers a chat prompt to set the **Sell Price**.
* **Currency Cycling:** **Middle-Clicking** an item cycles through available currency types (e.g., Coin, Token, Cash), allowing different items to use different economies.
* **Safety Saving:** When the editor is closed or the "Save" button is clicked, the system sanitizes the items (removing the editor-specific lore) and saves the clean data to the hard drive.

---

## 🎮 Commands & Usage

### General Commands
* `/market <name> [page]`
    * Opens the specific market GUI for the player.
    * *Example:* `/market ore` opens the Ore shop.

### Admin Commands
* `/market create <name>`
    * Creates a new, empty market file (e.g., `food.yml`).
    * Automatically opens the editor for the new market.
* `/market edit <name> [page]`
    * Opens the **Editor Mode** for the specified market.
    * Allows adding items, setting prices, and changing currencies.

---

## ⚙️ Technical Architecture

### Asynchronous Economy
The extension strictly adheres to thread-safety standards. All database transactions (adding/removing money) occur off the main server thread. This ensures that even if the database is slow, the Minecraft server will not freeze (TPS drop) during a transaction.

### Pagination System
The GUI supports infinite pages.
* Each page holds 45 items.
* Navigation buttons (Previous/Next) automatically appear in the bottom row if there are more items than fit on a single page.

### Clean Configuration Example
While the editor handles most setup, the resulting files are human-readable. An example `ore.yml` is generated automatically on first load:

```yaml
name: "Ore Market"
items:
  '1':
    buy:
      price: 50
    sell:
      price: 10
    currency: "coin"
    amount: 1
    metadata: "rO0ABXNyABpv..." # Base64 string of the item
```

## 📦 Build & Dependencies

* **Build System:** The project uses Gradle with dynamic versioning based on environment variables (Build Number, Release Tags).
* **Dependencies:**
  * **MCExtension:** The API allowing this to function as a module.
  * **MCEconomy-Common:** The bridge to the economy database.
  * **Paper API:** Optimized for the PaperMC server software.
