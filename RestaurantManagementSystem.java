import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


class RestaurantException extends Exception {
    public RestaurantException(String message) { super(message); }
}

enum UserRole {
    ADMIN("Admin"), 
    CASHIER("Cashier"),
    INVENTORY_MANAGER("Inventory Manager"); 

    private final String displayName;
    UserRole(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

enum PaymentMode {
    CASH("Cash"), CARD("Card"), UPI("UPI");
    private final String displayName;
    PaymentMode(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}

enum InventoryReason {
    REGULAR_RESTOCK("Regular Restock"),
    SPOILAGE_WASTAGE("Spoilage/Wastage"),
    INVENTORY_CORRECTION("Inventory Correction"),
    BATCH_COMPLETED("Batch Used/Completed");

    private final String displayName;
    InventoryReason(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
    @Override
    public String toString() { return displayName; }
}



class Ingredient implements Serializable {
    private static final long serialVersionUID = 2L;
    private final String name;
    private final String unit;

    public Ingredient(String name, String unit) {
        this.name = name;
        this.unit = unit;
    }

    public String getName() { return name; }
    public String getUnit() { return unit; }
    
    
    @Override
    public String toString() { return name; }
}

class StockBatch implements Serializable {
    private static final long serialVersionUID = 2L;
    private final String batchId;
    private final String ingredientName;
    private final Date arrivalDate;
    private final Date expiryDate;
    private final double initialQty;
    private double currentQty;
    private final double batchCost; 

    public StockBatch(String ingredientName, double initialQty, Date expiryDate, double batchCost) {
        this.batchId = UUID.randomUUID().toString();
        this.ingredientName = ingredientName;
        this.initialQty = initialQty;
        this.currentQty = initialQty;
        this.arrivalDate = new Date();
        this.expiryDate = expiryDate;
        this.batchCost = batchCost; 
    }

    public String getBatchId() { return batchId; }
    public String getIngredientName() { return ingredientName; }
    public Date getArrivalDate() { return arrivalDate; }
    public Date getExpiryDate() { return expiryDate; }
    public double getInitialQty() { return initialQty; }
    public double getCurrentQty() { return currentQty; }
    public double getBatchCost() { return batchCost; }

    public double getCostPerUnit() {
        if (initialQty == 0) return 0;
        return batchCost / initialQty;
    }

    public double deductStock(double amountToDeduct) {
        if (amountToDeduct >= currentQty) {
            double deducted = currentQty;
            currentQty = 0;
            return deducted;
        } else {
            currentQty -= amountToDeduct;
            return amountToDeduct;
        }
    }
}


class RecipeItem implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String ingredientName;
    private final double quantityNeeded;

    public RecipeItem(String ingredientName, double quantityNeeded) {
        this.ingredientName = ingredientName;
        this.quantityNeeded = quantityNeeded;
    }

    public String getIngredientName() { return ingredientName; }
    public double getQuantityNeeded() { return quantityNeeded; }

    
    @Override
    public String toString() {
        try {
            Ingredient ingredient = InventoryService.getInstance().getIngredientDefinition(ingredientName);
            String unit = (ingredient != null) ? ingredient.getUnit() : "units";
            return String.format("%s (%.2f %s)", ingredientName, quantityNeeded, unit);
        } catch (Exception e) {
            return String.format("%s (%.2f units)", ingredientName, quantityNeeded);
        }
    }
}

class Dish implements Serializable {
    private static final long serialVersionUID = 2L;
    private final String name;
    private double price;
    private final String category;
    private List<RecipeItem> recipe;

    public Dish(String name, double price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.recipe = new ArrayList<>();
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getCategory() { return category; }
    public void setPrice(double price) { this.price = price; }
    public List<RecipeItem> getRecipe() { return recipe; }
    public void setRecipe(List<RecipeItem> recipe) { this.recipe = recipe; }

    @Override
    public String toString() {
        return String.format("%s - ₹%.2f (%s)", name, price, category);
    }
}

class InventoryLog implements Serializable {
    private static final long serialVersionUID = 4L;
    private final Date timestamp;
    private final String ingredientName;
    private final String batchId;
    private final double quantityChange;
    private final String adminUsername;
    private final InventoryReason reason;
    private final Date expiryDate; 

    public InventoryLog(Date timestamp, String ingredientName, String batchId, double quantityChange, String adminUsername, InventoryReason reason, Date expiryDate) {
        this.timestamp = timestamp;
        this.ingredientName = ingredientName;
        this.batchId = batchId;
        this.quantityChange = quantityChange;
        this.adminUsername = adminUsername;
        this.reason = reason;
        this.expiryDate = expiryDate; 
    }

    public Date getTimestamp() { return timestamp; }
    public String getIngredientName() { return ingredientName; }
    public String getBatchId() { return batchId; }
    public double getQuantityChange() { return quantityChange; }
    public String getAdminUsername() { return adminUsername; }
    public InventoryReason getReason() { return reason; }
    public Date getExpiryDate() { return expiryDate; } 
}

class User implements Serializable {
    private static final long serialVersionUID = 2L; 
    private final String username;
    private final String password;     
    private final UserRole role;
    public User(String username, String password, UserRole role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }
    public boolean validatePassword(String password) {
        return this.password.equals(password);
    }
    public String getUsername() { return username; }
    public UserRole getRole() { return role; }
}

class OrderItem implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Dish dish;
    private int quantity;
    public OrderItem(Dish dish, int quantity) throws RestaurantException {
        if (quantity <= 0) {
            throw new RestaurantException("Quantity must be positive");
        }
        this.dish = dish;
        this.quantity = quantity;
    }

    // --- THIS IS THE FIXED METHOD ---
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Dish getDish() { return dish; }
    public int getQuantity() { return quantity; }
    public double getTotal() { return dish.getPrice() * quantity; }
}

class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<OrderItem> items;
    private String tableNumber;
    private double discount;
    private final long orderId;
    private Date orderTime; // No longer final
    private PaymentMode paymentMode;

    public Order() {
        items = new ArrayList<>();
        discount = 0.0;
        orderId = System.currentTimeMillis();
        orderTime = new Date();
        paymentMode = PaymentMode.CASH;
    }
    
    
    public void setOrderTime(Date time) {
        this.orderTime = time;
    }
    
    public void addItem(Dish dish, int quantity) throws RestaurantException {
        boolean found = false;
        for (OrderItem item : items) {
            if (item.getDish().getName().equals(dish.getName())) {
                item.setQuantity(item.getQuantity() + quantity); 
                found = true;
                break;
            }
        }
        if (!found) {
            items.add(new OrderItem(dish, quantity));
        }
    }
    public void removeItem(int index) throws RestaurantException {
        if (index < 0 || index >= items.size()) {
            throw new RestaurantException("Invalid item index");
        }
        items.remove(index);
    }
    public List<OrderItem> getItems() { return items; }
    public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }
    public String getTableNumber() { return tableNumber; }
    public void setDiscount(double discount) { this.discount = discount; }
    public double getDiscount() { return discount; }
    public Date getOrderTime() { return orderTime; }
    public long getOrderId() { return orderId; }     
    public void setPaymentMode(PaymentMode mode) { this.paymentMode = mode; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public double getSubtotal() {
        return items.stream().mapToDouble(OrderItem::getTotal).sum();
    }
    public double getGST() {
        return getSubtotal() * RestaurantManagementSystem.GST_RATE;
    }
    public double getGrandTotal() {
        double total = getSubtotal() + getGST() - discount;
        return Math.max(0, total);     
    }
    public void clear() {
        items.clear();
        discount = 0.0;
        tableNumber = null;
    }
}

class SaleRecord implements Serializable {
    private static final long serialVersionUID = 2L;
    private final Date saleTime;
    private final double grandTotal;
    private final double costOfGoodsSold;
    private final double profit;
    private final PaymentMode paymentMode; // NEW
    private final List<OrderItemData> itemsSold;
    
    public SaleRecord(Order order, double costOfGoodsSold) {
        this.saleTime = order.getOrderTime();
        this.grandTotal = order.getGrandTotal();
        this.costOfGoodsSold = costOfGoodsSold;
        this.profit = this.grandTotal - this.costOfGoodsSold;
        this.paymentMode = order.getPaymentMode(); // NEW
        this.itemsSold = order.getItems().stream()
            .map(item -> new OrderItemData(item.getDish().getName(), item.getQuantity()))
            .collect(Collectors.toList());
    }
    public Date getSaleTime() { return saleTime; }
    public double getGrandTotal() { return grandTotal; }
    public double getCostOfGoodsSold() { return costOfGoodsSold; }
    public double getProfit() { return profit; }
    public PaymentMode getPaymentMode() { return paymentMode; } // NEW
    public List<OrderItemData> getItemsSold() { return itemsSold; }
    
    public static class OrderItemData implements Serializable {
        private static final long serialVersionUID = 2L; 
        private final String dishName;
        private final int quantity;
        public OrderItemData(String dishName, int quantity) {
            this.dishName = dishName;
            this.quantity = quantity;
        }
        public String getDishName() { return dishName; }
        public int getQuantity() { return quantity; }
    }
}



class AuthService {
    private static final String USERS_FILE = "users_data.dat";
    private static AuthService instance;
    private Map<String, User> users;
    private User currentUser;
    private AuthService() {
        users = new HashMap<>();
        loadUsersFromFile();
        if (users.isEmpty()) {
            initializeDefaultUsers();
        }
    }
    public static AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }
    private void initializeDefaultUsers() {
        users.put("admin", new User("admin", "admin123", UserRole.ADMIN));
        users.put("cashier", new User("cashier", "cash123", UserRole.CASHIER));
        users.put("stock", new User("stock", "stock123", UserRole.INVENTORY_MANAGER));
        saveUsersToFile();
    }
    public User login(String username, String password) throws RestaurantException {
        User user = users.get(username.toLowerCase());
        if (user == null || !user.validatePassword(password)) {
            throw new RestaurantException("Invalid username or password");
        }
        currentUser = user;
        return user;
    }
    public void register(String username, String password, UserRole role) throws RestaurantException {
        if (username == null || username.trim().isEmpty()) {
            throw new RestaurantException("Username cannot be empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new RestaurantException("Password cannot be empty");
        }
        if (password.length() < 4) {
            throw new RestaurantException("Password must be at least 4 characters long");
        }
        String lowerUser = username.toLowerCase().trim();
        if (users.containsKey(lowerUser)) {
            throw new RestaurantException("Username already exists");
        }
        users.put(lowerUser, new User(lowerUser, password, role));
        saveUsersToFile();
    }
    public void logout() {
        currentUser = null;
    }
    public User getCurrentUser() { return currentUser; }
    private void loadUsersFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(USERS_FILE))) {
            @SuppressWarnings("unchecked")
            Map<String, User> loadedUsers = (Map<String, User>) ois.readObject();
            users.putAll(loadedUsers);
        } catch (FileNotFoundException e) {
            System.out.println("No user file found. Initializing defaults.");
        } catch (Exception e) {
            System.err.println("Error loading users: " + e.getMessage());
        }
    }
    private void saveUsersToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            oos.writeObject(users);
        } catch (Exception e) {
            System.err.println("Error saving users: " + e.getMessage());
        }
    }
}

class InventoryService {
    private static InventoryService instance;
    private Map<String, Ingredient> ingredientDefinitions;
    private List<StockBatch> stockBatches;
    private List<InventoryLog> inventoryLog;

    private InventoryService() {
        ingredientDefinitions = new HashMap<>();
        stockBatches = new ArrayList<>();
        inventoryLog = new ArrayList<>();
    }

    public static InventoryService getInstance() {
        if (instance == null) {
            instance = new InventoryService();
        }
        return instance;
    }

    public void clearAllData() {
        ingredientDefinitions.clear();
        stockBatches.clear();
        inventoryLog.clear();
    }
    
    public void addIngredientDefinition(Ingredient ingredient) throws RestaurantException {
        if (ingredientDefinitions.containsKey(ingredient.getName().toLowerCase())) {
            throw new RestaurantException("Ingredient definition already exists: " + ingredient.getName());
        }
        ingredientDefinitions.put(ingredient.getName().toLowerCase(), ingredient);
    }
    
    public Ingredient getIngredientDefinition(String name) {
        return ingredientDefinitions.get(name.toLowerCase());
    }

    public List<Ingredient> getAllIngredientDefinitions() {
        return new ArrayList<>(ingredientDefinitions.values());
    }

    public void addNewStockBatch(String ingredientName, double quantity, Date expiryDate, String user, double batchCost) throws RestaurantException {
        if (!ingredientDefinitions.containsKey(ingredientName.toLowerCase())) {
            throw new RestaurantException("Cannot add batch: Ingredient '" + ingredientName + "' is not defined.");
        }
        if (quantity <= 0) {
            throw new RestaurantException("Quantity must be positive.");
        }

        StockBatch batch = new StockBatch(ingredientName, quantity, expiryDate, batchCost);
        stockBatches.add(batch);

        InventoryLog log = new InventoryLog(new Date(), ingredientName, batch.getBatchId(),
            quantity, user, InventoryReason.REGULAR_RESTOCK, expiryDate);
        inventoryLog.add(log);

        saveStockBatchesToFile();
        saveInventoryLogToFile();
    }
    
    public void logStockAdjustment(StockBatch batch, double quantityChange, InventoryReason reason, String user) throws RestaurantException {
        if (quantityChange == 0) return;
        
        double deducted = batch.deductStock(Math.abs(quantityChange));
        if (quantityChange < 0) {
            deducted = -deducted;
        }

        InventoryLog log = new InventoryLog(new Date(), batch.getIngredientName(), batch.getBatchId(),
            deducted, user, reason, batch.getExpiryDate());
        inventoryLog.add(log);
        
        if (batch.getCurrentQty() <= 0) {
            logBatchCompletion(batch, user);
        }

        saveStockBatchesToFile();
        saveInventoryLogToFile();
    }
    
    private void logBatchCompletion(StockBatch batch, String user) {
        InventoryLog log = new InventoryLog(new Date(), batch.getIngredientName(), batch.getBatchId(),
            0, user, InventoryReason.BATCH_COMPLETED, batch.getExpiryDate());
        inventoryLog.add(log);
        
        stockBatches.remove(batch);
    }
    
    public List<InventoryLog> getInventoryHistory() {
        return new ArrayList<>(inventoryLog);
    }
    
    public List<StockBatch> getAllStockBatches() {
        return new ArrayList<>(stockBatches);
    }
    
    private List<StockBatch> getValidBatches(String ingredientName) {
        Date today = new Date();
        return stockBatches.stream()
            .filter(b -> b.getIngredientName().equalsIgnoreCase(ingredientName))
            .filter(b -> b.getCurrentQty() > 0)
            .filter(b -> b.getExpiryDate() == null || b.getExpiryDate().after(today))
            .sorted(Comparator.comparing(StockBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }

    public double getTotalStock(String ingredientName) {
        return getValidBatches(ingredientName).stream()
            .mapToDouble(StockBatch::getCurrentQty)
            .sum();
    }

    public int getEstimatedServings(Dish dish) {
        if (dish.getRecipe().isEmpty()) {
            return 0; 
        }
        int minServings = Integer.MAX_VALUE;
        for (RecipeItem item : dish.getRecipe()) {
            double totalStock = getTotalStock(item.getIngredientName());
            if (totalStock <= 0 || item.getQuantityNeeded() <= 0) {
                return 0;
            }
            int servingsForThis = (int) Math.floor(totalStock / item.getQuantityNeeded());
            if (servingsForThis < minServings) {
                minServings = servingsForThis;
            }
        }
        return (minServings == Integer.MAX_VALUE) ? 0 : minServings;
    }
    
    public boolean isDishAvailable(Dish dish, int quantity) {
        return getEstimatedServings(dish) >= quantity;
    }

    public double deductIngredientsForDish(Dish dish, int quantity) throws RestaurantException {
        if (!isDishAvailable(dish, quantity)) {
            throw new RestaurantException("Insufficient ingredients for " + dish.getName());
        }
        
        double totalCostForThisDish = 0.0;
        
        for (RecipeItem recipeItem : dish.getRecipe()) {
            double totalToDeduct = recipeItem.getQuantityNeeded() * quantity;
            List<StockBatch> batches = getValidBatches(recipeItem.getIngredientName());
            
            for (StockBatch batch : batches) {
                if (totalToDeduct <= 0) break;

                double amountToDeductFromThisBatch = Math.min(totalToDeduct, batch.getCurrentQty());
                batch.deductStock(amountToDeductFromThisBatch);
                
                double costPerUnit = batch.getCostPerUnit();
                totalCostForThisDish += (amountToDeductFromThisBatch * costPerUnit);
                
                totalToDeduct -= amountToDeductFromThisBatch;
                
                if (batch.getCurrentQty() <= 0) {
                    logBatchCompletion(batch, "System (Sale)");
                }
            }
        }
        saveStockBatchesToFile();
        saveInventoryLogToFile();
        
        return totalCostForThisDish;
    }
    
    public void loadIngredientDefinitionsFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(RestaurantManagementSystem.INGREDIENTS_FILE))) {
            @SuppressWarnings("unchecked")
            Map<String, Ingredient> loadedIngredients = (Map<String, Ingredient>) ois.readObject();
            ingredientDefinitions.putAll(loadedIngredients);
        } catch (FileNotFoundException e) {
            System.out.println("No ingredients definition file found. Starting fresh.");
        } catch (Exception e) {
            System.err.println("Error loading ingredient definitions: " + e.getMessage());
        }
    }

    public void saveIngredientDefinitionsToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(RestaurantManagementSystem.INGREDIENTS_FILE))) {
            oos.writeObject(ingredientDefinitions);
        } catch (Exception e) {
            System.err.println("Error saving ingredient definitions: " + e.getMessage());
        }
    }
    
    public void loadStockBatchesFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(RestaurantManagementSystem.STOCK_BATCHES_FILE))) {
            @SuppressWarnings("unchecked")
            List<StockBatch> loadedBatches = (List<StockBatch>) ois.readObject();
            stockBatches.clear();
            stockBatches.addAll(loadedBatches);
        } catch (FileNotFoundException e) {
            System.out.println("No stock batch file found. Starting fresh.");
        } catch (Exception e) {
            System.err.println("Error loading stock batches: " + e.getMessage());
        }
    }

    public void saveStockBatchesToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(RestaurantManagementSystem.STOCK_BATCHES_FILE))) {
            oos.writeObject(stockBatches);
        } catch (Exception e) {
            System.err.println("Error saving stock batches: " + e.getMessage());
        }
    }
    
    public void loadInventoryLogFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(RestaurantManagementSystem.INVENTORY_HISTORY_FILE))) {
            @SuppressWarnings("unchecked")
            List<InventoryLog> history = (List<InventoryLog>) ois.readObject();
            this.inventoryLog.clear();
            this.inventoryLog.addAll(history);
        } catch (FileNotFoundException e) {
            System.out.println("No inventory log file found. Starting fresh.");
        } catch (Exception e) {
            System.err.println("Error loading inventory log: " + e.getMessage());
        }
    }

    public void saveInventoryLogToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(RestaurantManagementSystem.INVENTORY_HISTORY_FILE))) {
            oos.writeObject(inventoryLog);
        } catch (Exception e) {
            System.err.println("Error saving inventory log: " + e.getMessage());
        }
    }
}

class Restaurant {
    private static Restaurant instance;
    private final List<Dish> menu;
    private final List<SaleRecord> salesHistory;

    private Restaurant() {
        menu = new ArrayList<>();
        salesHistory = new ArrayList<>();
    }
    
    public static Restaurant getInstance() {
        if (instance == null) {
            instance = new Restaurant();
        }
        return instance;
    }

    public void clearMenu() {
        menu.clear();
    }
    
    
    public void clearSalesHistory() {
        salesHistory.clear();
    }
    
    public void addDish(Dish dish) throws RestaurantException {
        if (dish == null) {
            throw new RestaurantException("Dish cannot be null");
        }
        for (Dish d : menu) {
            if (d.getName().equalsIgnoreCase(dish.getName())) {
                throw new RestaurantException("Dish already exists in menu");
            }
        }
        menu.add(dish);
    }
    
    public List<Dish> getMenu() {
        return new ArrayList<>(menu);
    }
    
    public Dish getDishByName(String name) {
        return menu.stream()
            .filter(d -> d.getName().equalsIgnoreCase(name))
            .findFirst().orElse(null);
    }
    
    public void removeDish(String dishName) throws RestaurantException {
        Dish toRemove = getDishByName(dishName);
        if (toRemove != null) {
            menu.remove(toRemove);
        } else {
            throw new RestaurantException("Dish not found: " + dishName);
        }
    }
    
    public void recordSale(Order order) throws RestaurantException {
        double totalCost = 0.0;
        
        for (OrderItem item : order.getItems()) {
            try {
                totalCost += InventoryService.getInstance().deductIngredientsForDish(item.getDish(), item.getQuantity());
            } catch (RestaurantException e) {
                throw new RestaurantException("Sale failed! " + e.getMessage());
            }
        }
        
        salesHistory.add(new SaleRecord(order, totalCost));
    }
        
    public void setSalesHistory(List<SaleRecord> history) {
        this.salesHistory.clear();
        this.salesHistory.addAll(history);
    }
        
    public List<SaleRecord> getSalesHistory() {
        return new ArrayList<>(salesHistory);
    }
}

class ImageBackgroundPanel extends JPanel {
    private Image backgroundImage;
    public ImageBackgroundPanel(LayoutManager layout) {
        super(layout);
        try {
            // --- THIS IS THE CORRECTED LINE ---
            backgroundImage = Toolkit.getDefaultToolkit().getImage("bg.jpeg"); 
        } catch (Exception e) {
            System.err.println("Error loading background image 'bg.jpeg'");
            setBackground(new Color(240, 240, 240));         
        }
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        }
    }
}



public class RestaurantManagementSystem extends JFrame {
    private static final String MENU_FILE = "restaurant_menu_basic.dat";
    private static final String SALES_FILE = "sales_history_basic.dat";
    public static final String INGREDIENTS_FILE = "ingredient_definitions.dat";
    public static final String STOCK_BATCHES_FILE = "stock_batches.dat";
    public static final String INVENTORY_HISTORY_FILE = "inventory_history.dat";
    
    private static final String BILLS_FOLDER = "Bills";     
    private static final String GST_NUMBER = "29ABCDE1234F1Z5";
    public static final double GST_RATE = 0.05;
    
    private final Restaurant restaurant;
    private final AuthService authService;
    private final InventoryService inventoryService;
    
    private final JPanel mainPanel;
    private final CardLayout cardLayout;

    public static final String[] MENU_CATEGORIES = {"Starter", "Main Course", "Beverage", "Dessert"};

    public RestaurantManagementSystem() {
        restaurant = Restaurant.getInstance();
        authService = AuthService.getInstance();
        inventoryService = InventoryService.getInstance();
        
        loadMenuFromFile();         
        loadSalesHistoryFromFile(); 
        inventoryService.loadIngredientDefinitionsFromFile();
        inventoryService.loadStockBatchesFromFile();
        inventoryService.loadInventoryLogFromFile();
        
        if (restaurant.getMenu().isEmpty() || inventoryService.getAllIngredientDefinitions().isEmpty()) {
            loadDefaultMenuAndIngredients();
        }
        
        setTitle("🍽️ DineEase - Restaurant POS System");
        setSize(1200, 800);         
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
                
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        
        mainPanel.add(new LoginPanel(), "LOGIN");
        mainPanel.add(new AdminPanel(), "ADMIN");
        mainPanel.add(new CashierPanel(), "CASHIER");
        mainPanel.add(new InventoryManagerPanel(), "INVENTORY_MANAGER");
        
        add(mainPanel);
        cardLayout.show(mainPanel, "LOGIN");
        setVisible(true);
    }
        
    class LoginPanel extends ImageBackgroundPanel {
        
        public LoginPanel() {
            super(new GridBagLayout()); 
            setOpaque(false); 
            
            JPanel loginBox = new JPanel();
            loginBox.setLayout(new BoxLayout(loginBox, BoxLayout.Y_AXIS));
            loginBox.setBackground(new Color(255, 255, 255, 220)); 
            loginBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(40, 50, 40, 50)
            ));
            
            JLabel titleLabel = new JLabel("Welcome to DineEase");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
            titleLabel.setForeground(new Color(50, 50, 50));
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            loginBox.add(titleLabel);
            loginBox.add(Box.createRigidArea(new Dimension(0, 30)));
            
            JButton adminLoginBtn = createStyledButton("Admin Login", new Color(52, 152, 219));
            adminLoginBtn.addActionListener(e -> showLogin(UserRole.ADMIN));
            loginBox.add(adminLoginBtn);
            loginBox.add(Box.createRigidArea(new Dimension(0, 15)));
            
            JButton adminRegisterBtn = createStyledButton("Register as Admin", new Color(46, 204, 113));
            adminRegisterBtn.addActionListener(e -> showRegister(UserRole.ADMIN));
            loginBox.add(adminRegisterBtn);
            loginBox.add(Box.createRigidArea(new Dimension(0, 30)));
            
            JButton cashierLoginBtn = createStyledButton("Cashier Login", new Color(155, 89, 182));
            cashierLoginBtn.addActionListener(e -> showLogin(UserRole.CASHIER));
            loginBox.add(cashierLoginBtn);
            loginBox.add(Box.createRigidArea(new Dimension(0, 15)));
            
            JButton cashierRegisterBtn = createStyledButton("Register as Cashier", new Color(241, 196, 15));
            cashierRegisterBtn.addActionListener(e -> showRegister(UserRole.CASHIER));
            loginBox.add(cashierRegisterBtn);
            loginBox.add(Box.createRigidArea(new Dimension(0, 30)));
            
            JButton inventoryLoginBtn = createStyledButton("Inventory Manager Login", new Color(230, 126, 34));
            inventoryLoginBtn.addActionListener(e -> showLogin(UserRole.INVENTORY_MANAGER));
            loginBox.add(inventoryLoginBtn);
            loginBox.add(Box.createRigidArea(new Dimension(0, 15)));
            
            JButton inventoryRegisterBtn = createStyledButton("Register as Inventory Manager", new Color(231, 76, 60));
            inventoryRegisterBtn.addActionListener(e -> showRegister(UserRole.INVENTORY_MANAGER));
            loginBox.add(inventoryRegisterBtn);

            add(loginBox);
        }
        
        private JButton createStyledButton(String text, Color bgColor) {
            JButton button = new JButton(text);
            button.setFont(new Font("Arial", Font.BOLD, 16));
            button.setBackground(bgColor);
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setMaximumSize(new Dimension(300, 50));
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return button;
        }
        
        private void showLogin(UserRole expectedRole) {
            JTextField usernameField = new JTextField(15);
            JPasswordField passwordField = new JPasswordField(15);
            JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
            panel.add(new JLabel("Username:"));
            panel.add(usernameField);
            panel.add(new JLabel("Password:"));
            panel.add(passwordField);
            int result = JOptionPane.showConfirmDialog(this, panel,
                expectedRole.getDisplayName() + " Login", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                try {
                    User user = authService.login(usernameField.getText(),
                        new String(passwordField.getPassword()));
                    if (user.getRole() == expectedRole) {
                        cardLayout.show(mainPanel, expectedRole.toString());
                    } else {
                        JOptionPane.showMessageDialog(this,
                            "Access denied! Invalid role.", "Error", JOptionPane.ERROR_MESSAGE);
                        authService.logout();                     
                    }
                } catch (RestaurantException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "Login Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void showRegister(UserRole role) {
            JTextField usernameField = new JTextField(15);
            JPasswordField passwordField = new JPasswordField(15);
            JPasswordField confirmPasswordField = new JPasswordField(15);
            JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5));
            panel.add(new JLabel("Username:"));
            panel.add(usernameField);
            panel.add(new JLabel("Password:"));
            panel.add(passwordField);
            panel.add(new JLabel("Confirm Password:"));
            panel.add(confirmPasswordField);
            panel.add(new JLabel("Role:"));
            panel.add(new JLabel(role.getDisplayName()));
            int result = JOptionPane.showConfirmDialog(this, panel,
                "Register as " + role.getDisplayName(), JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                try {
                    String username = usernameField.getText().trim();
                    String password = new String(passwordField.getPassword());
                    String confirmPassword = new String(confirmPasswordField.getPassword());
                    if (!password.equals(confirmPassword)) {
                        throw new RestaurantException("Passwords do not match!");
                    }
                    authService.register(username, password, role);
                    JOptionPane.showMessageDialog(this,
                        "Registration successful!\nYou can now login with your credentials.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (RestaurantException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "Registration Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
    
    class AdminPanel extends JPanel {         
        public AdminPanel() {
            super(new BorderLayout());             
            setBackground(new Color(240, 240, 240)); 
            
            JPanel headerPanel = createHeader("Admin Dashboard ⚙️", new Color(52, 152, 219));
            add(headerPanel, BorderLayout.NORTH);
            
            JTabbedPane tabbedPane = new JTabbedPane();
            
            MenuManagementPanel menuPanel = new MenuManagementPanel();
            IngredientDefinitionPanel ingredientDefPanel = new IngredientDefinitionPanel();
            BatchManagementPanel batchPanel = new BatchManagementPanel();
            SalesReportPanel salesPanel = new SalesReportPanel(); 
            TransactionLogPanel transactionLogPanel = new TransactionLogPanel();
            InventoryLogPanel inventoryLogPanel = new InventoryLogPanel(); 
            
            tabbedPane.addTab("Menu Management", menuPanel);
            tabbedPane.addTab("Ingredient Definitions", ingredientDefPanel);
            tabbedPane.addTab("Batch Management", batchPanel);
            tabbedPane.addTab("Profit & Sales Report 📈", salesPanel);
            tabbedPane.addTab("Transaction Log 🧾", transactionLogPanel);
            tabbedPane.addTab("Inventory Log 📦", inventoryLogPanel); 

            tabbedPane.addChangeListener(e -> {
                Component selected = tabbedPane.getSelectedComponent();
                if (selected == salesPanel) salesPanel.refreshReports();
                else if (selected == inventoryLogPanel) inventoryLogPanel.refreshLog();
                else if (selected == menuPanel) menuPanel.refreshMenuTable();
                else if (selected == ingredientDefPanel) ingredientDefPanel.refreshIngredientTable();
                else if (selected == batchPanel) batchPanel.refreshBatchTable();
                else if (selected == transactionLogPanel) transactionLogPanel.refreshLog();
            });
            add(tabbedPane, BorderLayout.CENTER);
        }
                
        private JPanel createHeader(String title, Color bgColor) {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(bgColor);
            headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            JLabel headerLabel = new JLabel(title);
            headerLabel.setFont(new Font("Arial", Font.BOLD, 24));
            headerLabel.setForeground(Color.WHITE);
            headerPanel.add(headerLabel, BorderLayout.WEST);
            JButton logoutBtn = new JButton("Logout");
            logoutBtn.addActionListener(e -> {
                authService.logout();
                cardLayout.show(mainPanel, "LOGIN");
            });
            headerPanel.add(logoutBtn, BorderLayout.EAST);
            return headerPanel;
        }
    }

    class InventoryManagerPanel extends JPanel {
        public InventoryManagerPanel() {
            super(new BorderLayout());             
            setBackground(new Color(240, 240, 240)); 
            
            JPanel headerPanel = createHeader("Stock Management 📦", new Color(230, 126, 34));
            add(headerPanel, BorderLayout.NORTH);
            
            JTabbedPane tabbedPane = new JTabbedPane();
            
            IngredientDefinitionPanel ingredientDefPanel = new IngredientDefinitionPanel();
            BatchManagementPanel batchPanel = new BatchManagementPanel();
            InventoryLogPanel inventoryLogPanel = new InventoryLogPanel(); 
            
            tabbedPane.addTab("Ingredient Definitions", ingredientDefPanel);
            tabbedPane.addTab("Batch Management", batchPanel);
            tabbedPane.addTab("Inventory Log", inventoryLogPanel); 

            tabbedPane.addChangeListener(e -> {
                Component selected = tabbedPane.getSelectedComponent();
                if (selected == inventoryLogPanel) inventoryLogPanel.refreshLog();
                else if (selected == ingredientDefPanel) ingredientDefPanel.refreshIngredientTable();
                else if (selected == batchPanel) batchPanel.refreshBatchTable();
            });
            add(tabbedPane, BorderLayout.CENTER);
        }

        private JPanel createHeader(String title, Color bgColor) {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(bgColor);
            headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            JLabel headerLabel = new JLabel(title);
            headerLabel.setFont(new Font("Arial", Font.BOLD, 24));
            headerLabel.setForeground(Color.WHITE);
            headerPanel.add(headerLabel, BorderLayout.WEST);
            JButton logoutBtn = new JButton("Logout");
            logoutBtn.addActionListener(e -> {
                authService.logout();
                cardLayout.show(mainPanel, "LOGIN");
            });
            headerPanel.add(logoutBtn, BorderLayout.EAST);
            return headerPanel;
        }
    }

    class MenuManagementPanel extends JPanel {
        private JTabbedPane menuTabbedPane;
        private Map<String, DefaultTableModel> categoryTableModels;

        public MenuManagementPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            categoryTableModels = new HashMap<>();
            menuTabbedPane = new JTabbedPane();

            String[] columns = {"Dish Name", "Price (₹)", "Category"}; 
            
            for (String category : RestaurantManagementSystem.MENU_CATEGORIES) {
                DefaultTableModel model = new DefaultTableModel(columns, 0) {
                    @Override
                    public boolean isCellEditable(int row, int column) { return false; }
                };
                categoryTableModels.put(category, model);
                
                JTable table = new JTable(model);
                table.setRowHeight(25);
                table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                
                JScrollPane scroll = new JScrollPane(table);           
                
                menuTabbedPane.addTab(category, scroll);
            }
            
            add(menuTabbedPane, BorderLayout.CENTER);
            
            JPanel buttonPanel = createButtons(); 
            add(buttonPanel, BorderLayout.SOUTH);
            SwingUtilities.invokeLater(this::refreshMenuTable);
        }
        
        private JTable getActiveMenuTable() {
            JScrollPane scrollPane = (JScrollPane) menuTabbedPane.getSelectedComponent();
            return (JTable) scrollPane.getViewport().getView();
        }
        
        private JPanel createButtons() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            
            JButton addBtn = new JButton("Add Dish");
            addBtn.addActionListener(e -> addDish());
            panel.add(addBtn);
            
            JButton removeBtn = new JButton("Remove Dish");
            removeBtn.addActionListener(e -> removeDish());
            panel.add(removeBtn);

            JButton editRecipeBtn = new JButton("Edit Recipe");
            editRecipeBtn.addActionListener(e -> editRecipe());
            panel.add(editRecipeBtn);
            
            return panel;
        }
        
        private void addDish() {
            JTextField nameField = new JTextField(20);
            JTextField priceField = new JTextField(10);
            JComboBox<String> categoryBox = new JComboBox<>(RestaurantManagementSystem.MENU_CATEGORIES);
            
            JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
            panel.add(new JLabel("Dish Name:")); panel.add(nameField);
            panel.add(new JLabel("Price (₹):")); panel.add(priceField);
            panel.add(new JLabel("Category:")); panel.add(categoryBox);
            
            int result = JOptionPane.showConfirmDialog(this, panel, "Add New Dish", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                try {
                    String name = nameField.getText().trim();
                    double price = Double.parseDouble(priceField.getText().trim());
                    String category = (String) categoryBox.getSelectedItem();
                    if (name.isEmpty() || price <= 0) {
                        throw new RestaurantException("Invalid input for dish details.");
                    }
                    
                    Dish dish = new Dish(name, price, category); 
                    restaurant.addDish(dish);
                    saveMenuToFile();
                    refreshMenuTable();
                    JOptionPane.showMessageDialog(this, "Dish added successfully!\nSelect it and click 'Edit Recipe' to add ingredients.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void removeDish() {
            JTable activeTable = getActiveMenuTable();
            int selectedRow = activeTable.getSelectedRow();
            
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a dish to remove");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this, "Remove this dish?",
                "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    int modelRow = activeTable.convertRowIndexToModel(selectedRow);
                    String dishName = (String) activeTable.getModel().getValueAt(modelRow, 0);
                    restaurant.removeDish(dishName);
                    saveMenuToFile();
                    refreshMenuTable();
                    JOptionPane.showMessageDialog(this, "Dish removed successfully!");
                } catch (RestaurantException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            }
        }

        private void editRecipe() {
            JTable activeTable = getActiveMenuTable();
            int selectedRow = activeTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a dish to edit its recipe.");
                return;
            }
            
            int modelRow = activeTable.convertRowIndexToModel(selectedRow);
            String dishName = (String) activeTable.getModel().getValueAt(modelRow, 0);

            Dish dishToEdit = restaurant.getDishByName(dishName);
            
            if (dishToEdit != null) {
                new RecipeEditorDialog(RestaurantManagementSystem.this, dishToEdit, () -> saveMenuToFile());
            }
        }
        
        public void refreshMenuTable() {
            for (DefaultTableModel model : categoryTableModels.values()) {
                model.setRowCount(0);
            }
            
            for (Dish dish : restaurant.getMenu()) {
                DefaultTableModel model = categoryTableModels.get(dish.getCategory());
                if (model != null) {
                    model.addRow(new Object[]{
                        dish.getName(),
                        String.format("%.2f", dish.getPrice()),
                        dish.getCategory()
                    });
                }
            }
        }
    }
            
    class IngredientDefinitionPanel extends JPanel {
        private DefaultTableModel ingredientTableModel;
        private JTable ingredientTable;
        private InventoryService invService = InventoryService.getInstance();

        public IngredientDefinitionPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            String[] columns = {"Ingredient Name", "Unit"};
            ingredientTableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            ingredientTable = new JTable(ingredientTableModel);
            ingredientTable.setRowHeight(25);
            JScrollPane scrollPane = new JScrollPane(ingredientTable);
            add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = createButtons();
            add(buttonPanel, BorderLayout.SOUTH);

            SwingUtilities.invokeLater(this::refreshIngredientTable);
        }

        private JPanel createButtons() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            
            JButton addBtn = new JButton("Add New Ingredient Type");
            addBtn.addActionListener(e -> addNewIngredient());
            panel.add(addBtn);
            
            return panel;
        }

        private void addNewIngredient() {
            JTextField nameField = new JTextField(20);
            JTextField unitField = new JTextField(10);
            
            JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
            panel.add(new JLabel("Ingredient Name:"));
            panel.add(nameField);
            panel.add(new JLabel("Unit (kg, L, pcs):"));
            panel.add(unitField);
            
            int result = JOptionPane.showConfirmDialog(this, panel, "Add New Ingredient Type", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                try {
                    String name = nameField.getText().trim();
                    String unit = unitField.getText().trim();
                    
                    if (name.isEmpty() || unit.isEmpty()) {
                        throw new RestaurantException("Name and Unit cannot be empty.");
                    }
                    
                    Ingredient ingredient = new Ingredient(name, unit);
                    invService.addIngredientDefinition(ingredient);
                    invService.saveIngredientDefinitionsToFile();
                    refreshIngredientTable();
                    JOptionPane.showMessageDialog(this, "Ingredient type added!");
                    
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        public void refreshIngredientTable() {
            ingredientTableModel.setRowCount(0);
            List<Ingredient> ingredients = invService.getAllIngredientDefinitions();
            ingredients.sort(Comparator.comparing(Ingredient::getName));
            for (Ingredient ingredient : ingredients) {
                ingredientTableModel.addRow(new Object[]{
                    ingredient.getName(),
                    ingredient.getUnit()
                });
            }
        }
    }
    
    class BatchManagementPanel extends JPanel {
        private DefaultTableModel batchTableModel;
        private JTable batchTable;
        private InventoryService invService = InventoryService.getInstance();
        private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        public BatchManagementPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            String[] columns = {"Ingredient", "Current Qty", "Unit", "Arrival Date", "Expiry Date", "Days Left"};
            batchTableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            batchTable = new JTable(batchTableModel);
            batchTable.setRowHeight(25);
            batchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            
            batchTable.getColumnModel().getColumn(5).setCellRenderer(new ExpiryRenderer());
            
            JScrollPane scrollPane = new JScrollPane(batchTable);
            add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = createButtons();
            add(buttonPanel, BorderLayout.SOUTH);

            SwingUtilities.invokeLater(this::refreshBatchTable);
        }

        private JPanel createButtons() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
            
            JButton addBtn = new JButton("Add New Batch");
            addBtn.addActionListener(e -> addNewBatch());
            panel.add(addBtn);

            JButton logBtn = new JButton("Log Spoilage/Adjustment");
            logBtn.addActionListener(e -> logAdjustment());
            panel.add(logBtn);
            
            return panel;
        }

        private void addNewBatch() {
            List<Ingredient> definitions = invService.getAllIngredientDefinitions();
            if (definitions.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No ingredient types defined. Please add one in 'Ingredient Definitions' first.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JComboBox<String> ingredientBox = new JComboBox<>(
                definitions.stream().map(Ingredient::getName).toArray(String[]::new)
            );
            
            JTextField quantityField = new JTextField(10);
            JTextField costField = new JTextField(10); 
            JTextField expiryField = new JTextField(10);
            
            JPanel panel = new JPanel(new GridLayout(4, 2, 5, 5)); 
            panel.add(new JLabel("Ingredient:"));
            panel.add(ingredientBox);
            panel.add(new JLabel("Quantity:"));
            panel.add(quantityField);
            panel.add(new JLabel("Total Cost (₹):")); 
            panel.add(costField); 
            panel.add(new JLabel("Expiry (yyyy-mm-dd):"));
            panel.add(expiryField);
            
            int result = JOptionPane.showConfirmDialog(this, panel, "Add New Stock Batch", JOptionPane.OK_CANCEL_OPTION);
            
            if (result == JOptionPane.OK_OPTION) {
                try {
                    String name = (String) ingredientBox.getSelectedItem();
                    double qty = Double.parseDouble(quantityField.getText().trim());
                    double cost = Double.parseDouble(costField.getText().trim()); 
                    String expiryStr = expiryField.getText().trim();
                    
                    if (cost < 0) {
                        throw new RestaurantException("Cost cannot be negative.");
                    }
                    
                    Date expiryDate = null;
                    if (!expiryStr.isEmpty()) {
                        expiryDate = new SimpleDateFormat("yyyy-MM-dd").parse(expiryStr);
                    }
                    
                    String user = authService.getCurrentUser().getUsername();
                    invService.addNewStockBatch(name, qty, expiryDate, user, cost); 
                    
                    refreshBatchTable();
                    JOptionPane.showMessageDialog(this, "New batch added and logged!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void logAdjustment() {
            int selectedRow = batchTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a batch to adjust.");
                return;
            }
            
            int modelRow = batchTable.convertRowIndexToModel(selectedRow);
            String batchId = (String) batchTableModel.getValueAt(modelRow, 6);
            
            StockBatch batch = invService.getAllStockBatches().stream()
                .filter(b -> b.getBatchId().equals(batchId))
                .findFirst().orElse(null);
                
            if (batch == null) {
                 JOptionPane.showMessageDialog(this, "Error: Could not find the selected batch. It may have been used or refreshed.", "Error", JOptionPane.ERROR_MESSAGE);
                 refreshBatchTable();
                 return;
            }

            JTextField quantityField = new JTextField(10);
            JComboBox<InventoryReason> reasonBox = new JComboBox<>(new InventoryReason[]{
                InventoryReason.SPOILAGE_WASTAGE, InventoryReason.INVENTORY_CORRECTION
            });
            
            JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
            panel.add(new JLabel("Ingredient:"));
            panel.add(new JLabel(batch.getIngredientName()));
            panel.add(new JLabel("Quantity to Remove (-):"));
            panel.add(quantityField);
            panel.add(new JLabel("Reason:"));
            panel.add(reasonBox);
            
            int result = JOptionPane.showConfirmDialog(this, panel, "Log Stock Adjustment", JOptionPane.OK_CANCEL_OPTION);
            
            if (result == JOptionPane.OK_OPTION) {
                try {
                    double qty = Double.parseDouble(quantityField.getText().trim());
                    if (qty > 0) qty = -qty;
                    if (qty == 0) return;
                    
                    InventoryReason reason = (InventoryReason) reasonBox.getSelectedItem();
                    String user = authService.getCurrentUser().getUsername();
                    
                    invService.logStockAdjustment(batch, qty, reason, user);
                    
                    refreshBatchTable();
                    JOptionPane.showMessageDialog(this, "Adjustment logged!");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Update Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        public void refreshBatchTable() {
            batchTableModel.setRowCount(0);
            String[] columns = {"Ingredient", "Current Qty", "Unit", "Arrival Date", "Expiry Date", "Days Left", "BatchID"};
            batchTableModel.setColumnIdentifiers(columns);
            
            List<StockBatch> batches = invService.getAllStockBatches();
            batches.sort(Comparator.comparing(StockBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())));
            
            for (StockBatch batch : batches) {
                Ingredient def = invService.getIngredientDefinition(batch.getIngredientName());
                String unit = (def != null) ? def.getUnit() : "?";
                long daysRemaining = getDaysRemaining(batch.getExpiryDate());
                
                batchTableModel.addRow(new Object[]{
                    batch.getIngredientName(),
                    batch.getCurrentQty(),
                    unit,
                    dateFormat.format(batch.getArrivalDate()),
                    (batch.getExpiryDate() == null) ? "N/A" : dateFormat.format(batch.getExpiryDate()),
                    daysRemaining,
                    batch.getBatchId()
                });
            }
            
            batchTable.getColumnModel().getColumn(6).setMinWidth(0);
            batchTable.getColumnModel().getColumn(6).setMaxWidth(0);
            batchTable.getColumnModel().getColumn(6).setWidth(0);
        }

        private long getDaysRemaining(Date expiryDate) {
            if (expiryDate == null) return Long.MAX_VALUE;
            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0);
            todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0);
            todayCal.set(Calendar.MILLISECOND, 0);
            Date today = todayCal.getTime();
            long diff = expiryDate.getTime() - today.getTime();
            return (long) Math.ceil(diff / (1000.0 * 60 * 60 * 24));
        }
    }
            
    class SalesReportPanel extends JPanel {
        private DefaultTableModel monthlyTableModel, yearlyTableModel;
        private JTable monthlyTable, yearlyTable;
        private JTextArea topItemsArea;
        private JLabel todayProfitLabel;
        private JLabel lastMonthProfitLabel;
        
        public SalesReportPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            JPanel atAGlancePanel = createAtAGlancePanel();
            add(atAGlancePanel, BorderLayout.NORTH);
            
            JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 10));

            JPanel reportsPanel = new JPanel(new GridLayout(2, 1, 10, 10));
            reportsPanel.add(createMonthlySalesPanel());
            reportsPanel.add(createYearlySalesPanel());
                                
            centerPanel.add(reportsPanel);
            centerPanel.add(createTopItemsPanel());
            
            add(centerPanel, BorderLayout.CENTER);
                                
            refreshReports();
        }

        private JPanel createAtAGlancePanel() {
            JPanel panel = new JPanel(new GridLayout(1, 2, 10, 10));
            panel.setBorder(BorderFactory.createTitledBorder("At a Glance"));
            
            todayProfitLabel = new JLabel("Today's Profit: ₹0.00");
            todayProfitLabel.setFont(new Font("Arial", Font.BOLD, 20));
            todayProfitLabel.setHorizontalAlignment(SwingConstants.CENTER);
            
            lastMonthProfitLabel = new JLabel("Last Month's Profit: ₹0.00");
            lastMonthProfitLabel.setFont(new Font("Arial", Font.BOLD, 20));
            lastMonthProfitLabel.setHorizontalAlignment(SwingConstants.CENTER);
            
            panel.add(todayProfitLabel);
            panel.add(lastMonthProfitLabel);
            
            return panel;
        }
        
        private JPanel createMonthlySalesPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createTitledBorder("Monthly Profit & Sales Summary (₹)"));
            
            String[] columns = {"Month/Year", "Revenue", "Cost", "Profit", "Orders"};
            
            monthlyTableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            monthlyTable = new JTable(monthlyTableModel);
            monthlyTable.setRowHeight(25);
            
            JScrollPane scrollPane = new JScrollPane(monthlyTable);
            panel.add(scrollPane, BorderLayout.CENTER);
            return panel;
        }

        private JPanel createYearlySalesPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createTitledBorder("Yearly Profit & Sales Summary (₹)"));
            
            String[] columns = {"Year", "Revenue", "Cost", "Profit", "Orders"};
            
            yearlyTableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            yearlyTable = new JTable(yearlyTableModel);
            yearlyTable.setRowHeight(25);
            JScrollPane scrollPane = new JScrollPane(yearlyTable);
            panel.add(scrollPane, BorderLayout.CENTER);
            return panel;
        }

        private JPanel createTopItemsPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createTitledBorder("Top 5 Selling Items (All Time)"));
            topItemsArea = new JTextArea();
            topItemsArea.setEditable(false);
            topItemsArea.setFont(new Font("Monospaced", Font.BOLD, 14));
            
            JScrollPane scrollPane = new JScrollPane(topItemsArea);
            panel.add(scrollPane, BorderLayout.CENTER);
            JButton refreshBtn = new JButton("Refresh Sales Data");
            refreshBtn.addActionListener(e -> refreshReports());
            panel.add(refreshBtn, BorderLayout.SOUTH);
            return panel;
        }
        
        public void refreshReports() {
            List<SaleRecord> history = Restaurant.getInstance().getSalesHistory(); 
            updateAtAGlance(history);
            updateMonthlySummary(history);
            updateYearlySummary(history);
            updateTopSellingItems(history);
        }
        
        private void updateAtAGlance(List<SaleRecord> history) {
            Calendar cal = Calendar.getInstance();
            Date today = cal.getTime();
            
            double todayProfit = history.stream()
                .filter(record -> isSameDay(record.getSaleTime(), today))
                .mapToDouble(SaleRecord::getProfit)
                .sum();
                
            todayProfitLabel.setText(String.format("Today's Profit: ₹%.2f", todayProfit));

            cal.add(Calendar.MONTH, -1);
            int lastMonth = cal.get(Calendar.MONTH);
            int lastMonthYear = cal.get(Calendar.YEAR);
            
            double lastMonthProfit = history.stream()
                .filter(record -> {
                    Calendar recordCal = Calendar.getInstance();
                    recordCal.setTime(record.getSaleTime());
                    return recordCal.get(Calendar.MONTH) == lastMonth &&
                           recordCal.get(Calendar.YEAR) == lastMonthYear;
                })
                .mapToDouble(SaleRecord::getProfit)
                .sum();
            
            lastMonthProfitLabel.setText(String.format("Last Month's Profit: ₹%.2f", lastMonthProfit));
        }
        
        private boolean isSameDay(Date date1, Date date2) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
            return fmt.format(date1).equals(fmt.format(date2));
        }

        private void updateMonthlySummary(List<SaleRecord> history) {
            monthlyTableModel.setRowCount(0);
                            
            Map<String, List<SaleRecord>> monthlySales = history.stream()
                .collect(Collectors.groupingBy(record -> new SimpleDateFormat("MMM yyyy").format(record.getSaleTime())));
            
            Map<Date, List<SaleRecord>> sortedMap = new TreeMap<>();
            for (Map.Entry<String, List<SaleRecord>> entry : monthlySales.entrySet()) {
                try {
                    sortedMap.put(new SimpleDateFormat("MMM yyyy").parse(entry.getKey()), entry.getValue());
                } catch (ParseException e) { e.printStackTrace(); }
            }

            if (sortedMap.isEmpty()) {
                monthlyTableModel.addRow(new Object[]{"No Sales Recorded", "", "", "", ""});
                return;
            }
            
            for (Map.Entry<Date, List<SaleRecord>> entry : sortedMap.entrySet()) {
                String monthYear = new SimpleDateFormat("MMM yyyy").format(entry.getKey());
                List<SaleRecord> records = entry.getValue();
                
                double totalRevenue = records.stream().mapToDouble(SaleRecord::getGrandTotal).sum();
                double totalCost = records.stream().mapToDouble(SaleRecord::getCostOfGoodsSold).sum();
                double totalProfit = records.stream().mapToDouble(SaleRecord::getProfit).sum();
                long totalOrders = records.size();
                
                monthlyTableModel.addRow(new Object[]{
                    monthYear,
                    String.format("₹%.2f", totalRevenue),
                    String.format("₹%.2f", totalCost),
                    String.format("₹%.2f", totalProfit),
                    totalOrders
                });
            }
        }
        
        private void updateYearlySummary(List<SaleRecord> history) {
            yearlyTableModel.setRowCount(0);
                            
            Map<String, List<SaleRecord>> yearlySales = history.stream()
                .collect(Collectors.groupingBy(record -> new SimpleDateFormat("yyyy").format(record.getSaleTime())));
            
            Map<String, List<SaleRecord>> sortedMap = new TreeMap<>(yearlySales);

            if (sortedMap.isEmpty()) {
                yearlyTableModel.addRow(new Object[]{"No Sales Recorded", "", "", "", ""});
                return;
            }
            
            for (Map.Entry<String, List<SaleRecord>> entry : sortedMap.entrySet()) {
                String year = entry.getKey();
                List<SaleRecord> records = entry.getValue();
                
                double totalRevenue = records.stream().mapToDouble(SaleRecord::getGrandTotal).sum();
                double totalCost = records.stream().mapToDouble(SaleRecord::getCostOfGoodsSold).sum();
                double totalProfit = records.stream().mapToDouble(SaleRecord::getProfit).sum();
                long totalOrders = records.size();
                
                yearlyTableModel.addRow(new Object[]{
                    year,
                    String.format("₹%.2f", totalRevenue),
                    String.format("₹%.2f", totalCost),
                    String.format("₹%.2f", totalProfit),
                    totalOrders
                });
            }
        }
                    
        private void updateTopSellingItems(List<SaleRecord> history) {
            Map<String, Integer> itemQuantities = new HashMap<>();
            for (SaleRecord record : history) {
                for (SaleRecord.OrderItemData item : record.getItemsSold()) {
                    itemQuantities.merge(item.getDishName(), item.getQuantity(), Integer::sum);
                }
            }
            List<Map.Entry<String, Integer>> topItems = itemQuantities.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5).collect(Collectors.toList());
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-30s %10s\n", "Dish Name", "Quantity"));                 
            sb.append("------------------------------------------\n");
            if (topItems.isEmpty()) {
                sb.append("No items sold yet.");
            } else {
                for (int i = 0; i < topItems.size(); i++) {
                    sb.append(String.format("%d. %-27s %10d\n", i + 1, topItems.get(i).getKey(), topItems.get(i).getValue()));                     
                }
            }
            topItemsArea.setText(sb.toString());
        }
    }
    
    
    class TransactionLogPanel extends JPanel {
        private DefaultTableModel transactionLogTableModel;
        private JTable transactionLogTable;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        public TransactionLogPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            String[] columns = {"Date/Time", "Items", "Revenue (₹)", "Cost (₹)", "Profit (₹)", "Payment Method"};
            
            transactionLogTableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            transactionLogTable = new JTable(transactionLogTableModel);
            transactionLogTable.setRowHeight(25);
            
            
            transactionLogTable.getColumnModel().getColumn(0).setMinWidth(150);
            transactionLogTable.getColumnModel().getColumn(0).setMaxWidth(150);
            transactionLogTable.getColumnModel().getColumn(1).setMinWidth(300);
            
            JScrollPane scrollPane = new JScrollPane(transactionLogTable);
            add(scrollPane, BorderLayout.CENTER);
            JButton refreshBtn = new JButton("Refresh Log");
            refreshBtn.addActionListener(e -> refreshLog());
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            bottomPanel.add(refreshBtn);
            add(bottomPanel, BorderLayout.SOUTH);
            SwingUtilities.invokeLater(this::refreshLog);
        }
        
        public void refreshLog() {
            transactionLogTableModel.setRowCount(0);
            List<SaleRecord> history = Restaurant.getInstance().getSalesHistory(); 
            
            history.sort(Comparator.comparing(SaleRecord::getSaleTime).reversed());
            
            if (history.isEmpty()) {
                transactionLogTableModel.addRow(new Object[]{"No transactions found.", "", "", "", "", ""});
            } else {
                for (SaleRecord record : history) {
                    String items = record.getItemsSold().stream()
                        .map(item -> String.format("%s (x%d)", item.getDishName(), item.getQuantity()))
                        .collect(Collectors.joining(", "));
                    
                    transactionLogTableModel.addRow(new Object[]{
                        dateFormat.format(record.getSaleTime()),
                        items,
                        String.format("%.2f", record.getGrandTotal()),
                        String.format("%.2f", record.getCostOfGoodsSold()),
                        String.format("%.2f", record.getProfit()),
                        record.getPaymentMode().getDisplayName()
                    });
                }
            }
        }
    }

    class InventoryLogPanel extends JPanel {
        private DefaultTableModel logTableModel;
        private JTable logTable;
        private SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

        public InventoryLogPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            String[] columns = {"Date/Time", "Ingredient", "Batch ID", "Qty Change", "User", "Reason"};
            
            logTableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            logTable = new JTable(logTableModel);
            logTable.setRowHeight(25);
            
            JScrollPane scrollPane = new JScrollPane(logTable);
            add(scrollPane, BorderLayout.CENTER);
            JButton refreshBtn = new JButton("Refresh Log");
            refreshBtn.addActionListener(e -> refreshLog());
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            bottomPanel.add(refreshBtn);
            add(bottomPanel, BorderLayout.SOUTH);
            SwingUtilities.invokeLater(this::refreshLog);
        }
        
        public void refreshLog() {
            logTableModel.setRowCount(0);
            List<InventoryLog> history = InventoryService.getInstance().getInventoryHistory(); 
            
            history.sort(Comparator.comparing(InventoryLog::getTimestamp).reversed());
            
            if (history.isEmpty()) {
                logTableModel.addRow(new Object[]{"No inventory history found.", "", "", "", "", ""});
            } else {
                for (InventoryLog log : history) {
                    logTableModel.addRow(new Object[]{
                        dateFormat.format(log.getTimestamp()),
                        log.getIngredientName(),
                        log.getBatchId().substring(0, 8) + "...",
                        log.getQuantityChange(),
                        log.getAdminUsername(),
                        log.getReason().getDisplayName()
                    });
                }
            }
        }
    }
        
    class CashierPanel extends JPanel {         
        private JTabbedPane menuTabbedPane;
        private Map<String, DefaultTableModel> categoryTableModels;
        
        private DefaultTableModel cartTableModel;
        private JTable cartTable;
        private Order currentOrder; 
        private JLabel subtotalLabel, gstLabel, discountLabel, grandTotalLabel;
        
        private static final int LOW_STOCK_WARNING = 5;
        
        public CashierPanel() {
            super(new BorderLayout(10, 10));             
            setBackground(new Color(240, 240, 240));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            currentOrder = new Order(); 
            
            categoryTableModels = new HashMap<>();

            JPanel headerPanel = createCashierHeader();
            add(headerPanel, BorderLayout.NORTH);
            
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            splitPane.setResizeWeight(0.5);
            
            splitPane.setLeftComponent(createMenuPanel());

            JPanel cartAndBillingPanel = new JPanel(new BorderLayout(10, 10));
            cartAndBillingPanel.add(createCartPanel(), BorderLayout.CENTER);
            cartAndBillingPanel.add(createBillingPanel(), BorderLayout.SOUTH);
            splitPane.setRightComponent(cartAndBillingPanel);
            
            add(splitPane, BorderLayout.CENTER);
            
            this.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && this.isShowing()) {
                    refreshMenu();
                    currentOrder = new Order();
                    refreshCart();
                }
            });
        }
        
        private JPanel createCashierHeader() {
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(new Color(155, 89, 182));
            header.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            JLabel title = new JLabel("💳 Cashier Terminal");
            title.setFont(new Font("Arial", Font.BOLD, 24));
            title.setForeground(Color.WHITE);
            header.add(title, BorderLayout.WEST);
            JButton logoutBtn = new JButton("Logout");
            logoutBtn.addActionListener(e -> {
                authService.logout();
                currentOrder.clear();
                cardLayout.show(mainPanel, "LOGIN");
            });
            header.add(logoutBtn, BorderLayout.EAST);
            return header;
        }
        
        private JPanel createMenuPanel() {
            JPanel menuPanel = new JPanel(new BorderLayout(5, 5));
            TitledBorder titledBorder = BorderFactory.createTitledBorder("Menu");
            menuPanel.setBorder(titledBorder);
            
            menuTabbedPane = new JTabbedPane();

            String[] columns = {"Dish", "Price", "Est. Qty"}; 

            for (String category : RestaurantManagementSystem.MENU_CATEGORIES) {
                DefaultTableModel model = new DefaultTableModel(columns, 0) {
                    @Override
                    public boolean isCellEditable(int row, int column) { return false; }
                };
                categoryTableModels.put(category, model);
                
                JTable table = new JTable(model);
                table.setRowHeight(25);
                table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                
                table.getColumnModel().getColumn(2).setCellRenderer(new StockQuantityRenderer());
                
                JScrollPane scroll = new JScrollPane(table);           
                
                menuTabbedPane.addTab(category, scroll);
            }
            
            menuPanel.add(menuTabbedPane, BorderLayout.CENTER);
            
            JButton addBtn = new JButton("Add to Cart");
            addBtn.addActionListener(e -> addToCart());
            menuPanel.add(addBtn, BorderLayout.SOUTH);
            
            return menuPanel;
        }
        
        private JPanel createCartPanel() {
            TitledBorder titledBorder = BorderFactory.createTitledBorder("Current Order");
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBorder(titledBorder);
            String[] columns = {"Item", "Qty", "Price", "Total"};
            cartTableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            cartTable = new JTable(cartTableModel);
            cartTable.setRowHeight(25);
            cartTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JScrollPane scroll = new JScrollPane(cartTable);
            panel.add(scroll, BorderLayout.CENTER);
            JPanel btnPanel = new JPanel(new FlowLayout());
            JButton removeBtn = new JButton("Remove");
            removeBtn.addActionListener(e -> removeFromCart());
            JButton clearBtn = new JButton("Clear");
            clearBtn.addActionListener(e -> clearCart());
            btnPanel.add(removeBtn);
            btnPanel.add(clearBtn);
            panel.add(btnPanel, BorderLayout.SOUTH);
            return panel;
        }

        private JPanel createBillingPanel() {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            TitledBorder titledBorder = BorderFactory.createTitledBorder("Bill Summary");
            JPanel summaryPanel = new JPanel(new GridLayout(4, 2, 10, 5));
            summaryPanel.setBorder(titledBorder);             
            JLabel subtotalLabelTitle = new JLabel("Subtotal:");
            summaryPanel.add(subtotalLabelTitle);
            subtotalLabel = new JLabel("₹0.00");
            summaryPanel.add(subtotalLabel);
            JLabel gstLabelTitle = new JLabel("GST (5%):");
            summaryPanel.add(gstLabelTitle);
            gstLabel = new JLabel("₹0.00");
            summaryPanel.add(gstLabel);
            JLabel discountLabelTitle = new JLabel("Discount:");
            summaryPanel.add(discountLabelTitle);
            discountLabel = new JLabel("₹0.00");
            summaryPanel.add(discountLabel);
            JLabel grandTotalLabelTitle = new JLabel("GRAND TOTAL:");
            grandTotalLabelTitle.setFont(new Font("Arial", Font.BOLD, 16));
            summaryPanel.add(grandTotalLabelTitle);
            grandTotalLabel = new JLabel("₹0.00");
            grandTotalLabel.setFont(new Font("Arial", Font.BOLD, 18));
            summaryPanel.add(grandTotalLabel);
            panel.add(summaryPanel, BorderLayout.CENTER);
            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
            JButton discountBtn = new JButton("Apply Discount");
            discountBtn.addActionListener(e -> applyDiscount());
            JButton billBtn = new JButton("Generate Bill");
            billBtn.addActionListener(e -> generateBill());
            actionPanel.add(discountBtn);
            actionPanel.add(billBtn);
            panel.add(actionPanel, BorderLayout.SOUTH);
            return panel;
        }
        
        private JTable getActiveMenuTable() {
            JScrollPane scrollPane = (JScrollPane) menuTabbedPane.getSelectedComponent();
            return (JTable) scrollPane.getViewport().getView();
        }

        private void addToCart() {
            JTable activeTable = getActiveMenuTable();
            int selectedRow = activeTable.getSelectedRow();
            
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a dish from the list");
                return;
            }
            String qtyStr = JOptionPane.showInputDialog(this, "Enter quantity:", "1");
            if (qtyStr != null && !qtyStr.trim().isEmpty()) {
                try {
                    int quantityToAdd = Integer.parseInt(qtyStr);
                    if (quantityToAdd <= 0) {
                        throw new RestaurantException("Quantity must be positive.");
                    }
                    
                    int modelRow = activeTable.convertRowIndexToModel(selectedRow);
                    String dishName = (String) activeTable.getModel().getValueAt(modelRow, 0);
                    
                    Dish selectedDish = restaurant.getDishByName(dishName);

                    if (selectedDish == null) {
                        throw new RestaurantException("Dish not found.");
                    }

                    int quantityInCart = 0;
                    for (OrderItem item : currentOrder.getItems()) {
                        if (item.getDish().getName().equals(selectedDish.getName())) {
                            quantityInCart = item.getQuantity();
                            break;
                        }
                    }
                    int totalQuantity = quantityInCart + quantityToAdd;
                    
                    int maxQty = InventoryService.getInstance().getEstimatedServings(selectedDish);
                    
                    if (totalQuantity > maxQty) {
                        if (maxQty == 0) {
                             throw new RestaurantException(selectedDish.getName() + " is out of stock!");
                        }
                        throw new RestaurantException("Not enough ingredients for " + totalQuantity + " " + selectedDish.getName() + "!\nOnly " + maxQty + " can be made in total.");
                    }
                    
                    currentOrder.addItem(selectedDish, quantityToAdd);
                    refreshCart();
                    
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: ".toUpperCase() + ex.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void removeFromCart() {
            int selectedRow = cartTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select an item");
                return;
            }
            try {
                int modelRow = cartTable.convertRowIndexToModel(selectedRow);
                currentOrder.removeItem(modelRow);
                refreshCart();
            } catch (RestaurantException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        }
        private void clearCart() {
            currentOrder.clear();
            refreshCart();
        }
        private void applyDiscount() {
            String input = JOptionPane.showInputDialog(this, "Enter discount amount (₹):", "0");
            if (input != null) {
                try {
                    double discount = Double.parseDouble(input);
                    if (discount < 0) throw new NumberFormatException("Discount cannot be negative.");
                    currentOrder.setDiscount(discount);
                    refreshCart();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Invalid discount amount: " + ex.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void saveBillTextFile(String bill) {
            try {
                File folder = new File(RestaurantManagementSystem.BILLS_FOLDER);
                if (!folder.exists()) folder.mkdirs();
                String filename = String.format("%s/Bill_%d.txt", BILLS_FOLDER, currentOrder.getOrderId());
                try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                    writer.println(bill);
                }
                System.out.println("Bill saved successfully to: " + filename);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving bill text file: " + ex.getMessage(), "File Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        
        private void generateBill() {
            if (currentOrder.getItems().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Cart is empty!");
                return;
            }
            
            String table = JOptionPane.showInputDialog(this, "Enter Table Number:");
            if (table == null || table.trim().isEmpty()) {
                 JOptionPane.showMessageDialog(this, "Table number is required.", "Input Error", JOptionPane.WARNING_MESSAGE);
                 return;
            }
            currentOrder.setTableNumber(table.trim());

            String[] paymentOptions = {"Cash", "Card", "UPI"};
            int payment = JOptionPane.showOptionDialog(this, "Select Payment Mode:", "Payment",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, paymentOptions, paymentOptions[0]);
            
            if (payment >= 0) {
                currentOrder.setPaymentMode(PaymentMode.values()[payment]);
                String billText = generateBillText();
                
                try {
                    restaurant.recordSale(currentOrder); 
                } catch (RestaurantException e) {
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Sale Failed", JOptionPane.ERROR_MESSAGE);
                    refreshMenu();
                    return;
                }

                saveSalesHistoryToFile(); 
                
                JTextArea billArea = new JTextArea(billText);
                billArea.setEditable(false);
                billArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
                JScrollPane scrollPane = new JScrollPane(billArea);
                scrollPane.setPreferredSize(new Dimension(500, 600));
                JOptionPane.showMessageDialog(this, scrollPane, "Bill Generated - Transaction Complete", JOptionPane.INFORMATION_MESSAGE);
                
                saveBillTextFile(billText);
                
                currentOrder = new Order();
                refreshCart();
                refreshMenu(); 
            }
        }
        
        private String generateBillText() {
            StringBuilder bill = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            
            bill.append("================================================\n");
            bill.append("                Welcome to DineEase                \n");
            bill.append("================================================\n");
            
            bill.append(String.format("GST No: %s\n", GST_NUMBER));
            bill.append(String.format("Date/Time: %s\n", sdf.format(currentOrder.getOrderTime())));
            bill.append(String.format("Table: %s\n", currentOrder.getTableNumber()));
            bill.append(String.format("Payment: %s\n", currentOrder.getPaymentMode().getDisplayName()));
            bill.append("================================================\n\n");
            bill.append(String.format("%-25s %5s %10s %12s\n", "Item", "Qty", "Price", "Total"));
            bill.append("------------------------------------------------\n");
            for (OrderItem item : currentOrder.getItems()) {
                bill.append(String.format("%-25s %5d %10.2f %12.2f\n",
                    item.getDish().getName(), item.getQuantity(),
                    item.getDish().getPrice(), item.getTotal()));
            }
            bill.append("------------------------------------------------\n");
            bill.append(String.format("%-43s %12.2f\n", "Subtotal:", currentOrder.getSubtotal()));
            bill.append(String.format("%-43s %12.2f\n", "GST (5%):", currentOrder.getGST()));
            if (currentOrder.getDiscount() > 0) {
                bill.append(String.format("%-43s -%11.2f\n", "Discount:", currentOrder.getDiscount()));
            }
            bill.append("================================================\n");
            bill.append(String.format("%-43s %12.2f\n", "Grand Total:", currentOrder.getGrandTotal()));
            bill.append("================================================\n");
            bill.append("            Thank you for visiting!             \n");
            return bill.toString();
        }
        
        private void refreshMenu() {
            for (DefaultTableModel model : categoryTableModels.values()) {
                model.setRowCount(0);
            }
            
            for (Dish dish : restaurant.getMenu()) {
                DefaultTableModel model = categoryTableModels.get(dish.getCategory());
                
                if (model != null) { 
                    int estStock = InventoryService.getInstance().getEstimatedServings(dish);
                    
                    model.addRow(new Object[]{
                        dish.getName(),
                        String.format("₹%.2f", dish.getPrice()),
                        estStock
                    });
                }
            }
        }
        
        private void refreshCart() {
            cartTableModel.setRowCount(0);
            for (OrderItem item : currentOrder.getItems()) {
                cartTableModel.addRow(new Object[]{
                    item.getDish().getName(),
                    item.getQuantity(),
                    String.format("₹%.2f", item.getDish().getPrice()),
                    String.format("₹%.2f", item.getTotal())
                });
            }
            subtotalLabel.setText(String.format("₹%.2f", currentOrder.getSubtotal()));
            gstLabel.setText(String.format("₹%.2f", currentOrder.getGST()));
            discountLabel.setText(String.format("₹%.2f", currentOrder.getDiscount()));
            grandTotalLabel.setText(String.format("₹%.2f", currentOrder.getGrandTotal()));
        }
        
        class StockQuantityRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                         boolean isSelected, boolean hasFocus,
                                                         int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (value instanceof Integer) {
                    int stock = (Integer) value;
                    
                    if (stock == 0) {
                        setText("Out of Stock");
                        c.setBackground(new Color(255, 102, 102)); 
                        c.setForeground(Color.WHITE);
                    } else if (stock <= LOW_STOCK_WARNING) {
                        setText(String.valueOf(stock));
                        c.setBackground(new Color(255, 215, 0)); 
                        c.setForeground(Color.BLACK);
                    } else {
                        setText(String.valueOf(stock));
                        c.setBackground(new Color(144, 238, 144));
                        c.setForeground(Color.BLACK);
                    }
                    
                    if (isSelected) {
                        c.setBackground(table.getSelectionBackground());
                        c.setForeground(table.getSelectionForeground());
                    }
                }
                
                ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        }
    }
    
    class ExpiryRenderer extends DefaultTableCellRenderer {
        private static final int EXPIRY_WARNING_DAYS = 7;
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                     boolean isSelected, boolean hasFocus,
                                                     int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (value instanceof Long) {
                long days = (Long) value;
                
                if (days == Long.MAX_VALUE || days > 365000) { 
                    setText("N/A");
                    c.setBackground(table.getBackground());
                    c.setForeground(table.getForeground());
                } else if (days <= 0) {
                    setText("EXPIRED");
                    c.setBackground(new Color(255, 102, 102)); 
                    c.setForeground(Color.WHITE);
                } else if (days <= EXPIRY_WARNING_DAYS) {
                    setText(days + " days");
                    c.setBackground(new Color(255, 215, 0)); 
                    c.setForeground(Color.BLACK);
                } else {
                    setText(days + " days");
                    c.setBackground(new Color(144, 238, 144)); 
                    c.setForeground(Color.BLACK);
                }
                
                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                }
            }
            
            ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
            return c;
        }
    }
    
    class RecipeEditorDialog extends JDialog {
        private Dish dish;
        private InventoryService invService = InventoryService.getInstance();
        private DefaultListModel<RecipeItem> recipeListModel;
        private JList<RecipeItem> recipeList;
        private JComboBox<Ingredient> ingredientComboBox;
        private JTextField quantityField;
        private Runnable onSaveCallback;

        public RecipeEditorDialog(JFrame parent, Dish dishToEdit, Runnable onSaveCallback) {
            super(parent, "Edit Recipe for: " + dishToEdit.getName(), true);
            this.dish = dishToEdit;
            this.onSaveCallback = onSaveCallback;
            
            setSize(600, 400);
            setLocationRelativeTo(parent);
            setLayout(new BorderLayout(10, 10));
            
            ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

            // 1. Current Recipe Panel (Left)
            recipeListModel = new DefaultListModel<>();
            dish.getRecipe().forEach(recipeListModel::addElement);
            recipeList = new JList<>(recipeListModel);
            JScrollPane listScrollPane = new JScrollPane(recipeList);
            listScrollPane.setBorder(BorderFactory.createTitledBorder("Current Recipe"));
            add(listScrollPane, BorderLayout.CENTER);

            // 2. Add Ingredient Panel (Right)
            JPanel addPanel = new JPanel(new GridBagLayout());
            addPanel.setBorder(BorderFactory.createTitledBorder("Add Ingredient"));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0; gbc.gridy = 0;
            addPanel.add(new JLabel("Ingredient:"), gbc);
            
            gbc.gridx = 1; gbc.gridy = 0;
            ingredientComboBox = new JComboBox<>(
                invService.getAllIngredientDefinitions().toArray(new Ingredient[0])
            );
            addPanel.add(ingredientComboBox, gbc);

            gbc.gridx = 0; gbc.gridy = 1;
            addPanel.add(new JLabel("Quantity:"), gbc);
            
            gbc.gridx = 1; gbc.gridy = 1;
            quantityField = new JTextField(5);
            addPanel.add(quantityField, gbc);

            gbc.gridx = 1; gbc.gridy = 2;
            JButton addButton = new JButton("Add");
            addButton.addActionListener(e -> addIngredientToRecipe());
            addPanel.add(addButton, gbc);
            
            gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(20, 5, 5, 5);
            addPanel.add(new JLabel("--- OR ---"), gbc);
            
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
            gbc.insets = new Insets(5, 5, 5, 5);
            JButton removeButton = new JButton("Remove Selected Ingredient");
            removeButton.addActionListener(e -> removeIngredientFromRecipe());
            addPanel.add(removeButton, gbc);
            
            add(addPanel, BorderLayout.EAST);

            // 3. Bottom Panel (Save/Cancel)
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveButton = new JButton("Save & Close");
            saveButton.addActionListener(e -> saveRecipe());
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> dispose());
            
            bottomPanel.add(cancelButton);
            bottomPanel.add(saveButton);
            add(bottomPanel, BorderLayout.SOUTH);

            setVisible(true);
        }
        
        private void addIngredientToRecipe() {
            try {
                Ingredient selectedIngredient = (Ingredient) ingredientComboBox.getSelectedItem();
                if (selectedIngredient == null) {
                    throw new RestaurantException("Please select an ingredient.");
                }
                double quantity = Double.parseDouble(quantityField.getText());
                if (quantity <= 0) {
                    throw new RestaurantException("Quantity must be positive.");
                }
                
                for (int i = 0; i < recipeListModel.getSize(); i++) {
                    if (recipeListModel.getElementAt(i).getIngredientName().equals(selectedIngredient.getName())) {
                        throw new RestaurantException(selectedIngredient.getName() + " is already in the recipe. Remove it first to update.");
                    }
                }
                
                RecipeItem newItem = new RecipeItem(selectedIngredient.getName(), quantity);
                recipeListModel.addElement(newItem);
                quantityField.setText("");
                
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid quantity. Please enter a number.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (RestaurantException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        
        private void removeIngredientFromRecipe() {
            RecipeItem selectedItem = recipeList.getSelectedValue();
            if (selectedItem == null) {
                JOptionPane.showMessageDialog(this, "Please select an ingredient from the list to remove.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            recipeListModel.removeElement(selectedItem);
        }
        
        private void saveRecipe() {
            List<RecipeItem> newRecipe = new ArrayList<>();
            for (int i = 0; i < recipeListModel.getSize(); i++) {
                newRecipe.add(recipeListModel.getElementAt(i));
            }
            
            dish.setRecipe(newRecipe);
            
            onSaveCallback.run();
            
            JOptionPane.showMessageDialog(this, "Recipe for " + dish.getName() + " saved!");
            dispose();
        }
    }

    
    
    private void loadDefaultMenuAndIngredients() {
        try {
            InventoryService inv = InventoryService.getInstance();
            inv.clearAllData();
            restaurant.clearMenu();
            restaurant.clearSalesHistory();

            
            inv.addIngredientDefinition(new Ingredient("Chicken", "kg"));
            inv.addIngredientDefinition(new Ingredient("Paneer", "kg"));
            inv.addIngredientDefinition(new Ingredient("Naan Flour", "kg"));
            inv.addIngredientDefinition(new Ingredient("Rice", "kg"));
            inv.addIngredientDefinition(new Ingredient("Cream", "L"));
            inv.addIngredientDefinition(new Ingredient("Sugar Syrup", "L"));
            inv.addIngredientDefinition(new Ingredient("Coke Can", "pcs"));
            
            
            String user = "default_setup";
            inv.addNewStockBatch("Chicken", 100.0, parseDate("2025-11-20"), user, 20000.0); // 200/kg
            inv.addNewStockBatch("Paneer", 50.0, parseDate("2025-11-15"), user, 15000.0); // 300/kg
            inv.addNewStockBatch("Naan Flour", 200.0, null, user, 8000.0); // 40/kg
            inv.addNewStockBatch("Rice", 250.0, null, user, 20000.0); // 80/kg
            inv.addNewStockBatch("Cream", 5.0, parseDate("2025-11-08"), user, 1000.0); // 200/L (FIXED: was 0.15L)
            inv.addNewStockBatch("Sugar Syrup", 30.0, null, user, 3000.0); // 100/L
            inv.addNewStockBatch("Coke Can", 500.0, null, user, 15000.0); // 30/pc
            
            
            Dish pTikka = new Dish("Paneer Tikka", 250.0, MENU_CATEGORIES[0]); // Starter
            pTikka.setRecipe(Arrays.asList(new RecipeItem("Paneer", 0.2)));
            restaurant.addDish(pTikka);
            
            Dish bChicken = new Dish("Butter Chicken", 320.0, MENU_CATEGORIES[1]); // Main Course
            bChicken.setRecipe(Arrays.asList(new RecipeItem("Chicken", 0.25), new RecipeItem("Cream", 0.1)));
            restaurant.addDish(bChicken);
            
            Dish biryani = new Dish("Biryani", 280.0, MENU_CATEGORIES[1]); // Main Course
            biryani.setRecipe(Arrays.asList(new RecipeItem("Rice", 0.3), new RecipeItem("Chicken", 0.15)));
            restaurant.addDish(biryani);

            Dish naan = new Dish("Naan", 40.0, MENU_CATEGORIES[1]); // Main Course
            naan.setRecipe(Arrays.asList(new RecipeItem("Naan Flour", 0.1)));
            restaurant.addDish(naan);
            
            Dish coke = new Dish("Coke", 60.0, MENU_CATEGORIES[2]); // Beverage
            coke.setRecipe(Arrays.asList(new RecipeItem("Coke Can", 1)));
            restaurant.addDish(coke);
            
            Dish gJamun = new Dish("Gulab Jamun", 80.0, MENU_CATEGORIES[3]); // Dessert
            gJamun.setRecipe(Arrays.asList(new RecipeItem("Sugar Syrup", 0.05)));
            restaurant.addDish(gJamun);

            
            addFakeSalesHistory();
            
            
            saveMenuToFile();
            inv.saveIngredientDefinitionsToFile();
            inv.saveStockBatchesToFile();
            inv.saveInventoryLogToFile();
            saveSalesHistoryToFile(); 
            
        } catch (RestaurantException e) {
            System.err.println("Error loading default items: " + e.getMessage());
        }
    }
    
    
    private void addFakeSalesHistory() {
      
        Dish bChickenSale = restaurant.getDishByName("Butter Chicken");
        Dish biryaniSale = restaurant.getDishByName("Biryani");
        Dish naanSale = restaurant.getDishByName("Naan");
        Dish cokeSale = restaurant.getDishByName("Coke");
        Dish gJamunSale = restaurant.getDishByName("Gulab Jamun");
        
        
        double bChickenCost = (0.25 * 200) + (0.1 * 200); 
        double biryaniCost = (0.3 * 80) + (0.15 * 200); 
        double naanCost = 0.1 * 40; 
        double cokeCost = 1 * 30; 
        double gJamunCost = 0.05 * 100; 
        
        Random rand = new Random();
        List<SaleRecord> fakeHistory = new ArrayList<>();

        for (int i = 1; i <= 28; i++) { 
            Date saleDate = getOctDate(i);
            int salesPerDay = 5 + rand.nextInt(10); 
            
            for(int j = 0; j < salesPerDay; j++) {
                try {
                    Order order = new Order();
                    order.setOrderTime(saleDate);
                    order.setPaymentMode(PaymentMode.values()[rand.nextInt(3)]); 
                    
                    double orderCost = 0;
                    
                    
                    if (rand.nextBoolean()) {
                        order.addItem(bChickenSale, 1);
                        orderCost += bChickenCost;
                    } else {
                        order.addItem(biryaniSale, 1);
                        orderCost += biryaniCost;
                    }
                    
                    
                    int naanQty = 1 + rand.nextInt(2);
                    order.addItem(naanSale, naanQty);
                    orderCost += naanCost * naanQty;
                    
                    
                    if (rand.nextBoolean()) {
                        int cokeQty = 1 + rand.nextInt(2);
                        order.addItem(cokeSale, cokeQty);
                        orderCost += cokeCost * cokeQty;
                    }

                    
                    if (rand.nextDouble() < 0.3) {
                        order.addItem(gJamunSale, 1);
                        orderCost += gJamunCost;
                    }
                    
                    
                    fakeHistory.add(new SaleRecord(order, orderCost));

                } catch (RestaurantException e) {
                    System.err.println("Error generating fake sale: " + e.getMessage());
                }
            }
        }
        restaurant.setSalesHistory(fakeHistory);
    }
    
   
    private Date getOctDate(int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2025);
        cal.set(Calendar.MONTH, Calendar.OCTOBER);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 12 + new Random().nextInt(10)); 
        cal.set(Calendar.MINUTE, new Random().nextInt(60));
        return cal.getTime();
    }
    
    private Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
        } catch (ParseException e) {
            return null;
        }
    }
    
    private void loadMenuFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(MENU_FILE))) {
            @SuppressWarnings("unchecked")
            List<Dish> dishes = (List<Dish>) ois.readObject();
            for (Dish dish : dishes) {
                try { restaurant.addDish(dish); } catch (RestaurantException ignored) {}
            }
        } catch (FileNotFoundException e) {
             System.out.println("No menu file found. Will load defaults.");
        } catch (Exception e) {
            System.err.println("Error loading menu: " + e.getMessage());
        }
    }
    private void saveMenuToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(MENU_FILE))) {
            oos.writeObject(restaurant.getMenu());
        } catch (Exception e) {
            System.err.println("Error saving menu: " + e.getMessage());
        }
    }
    
    private void loadSalesHistoryFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SALES_FILE))) {
            @SuppressWarnings("unchecked")
            List<SaleRecord> history = (List<SaleRecord>) ois.readObject();
            restaurant.setSalesHistory(history);
        } catch (FileNotFoundException e) {
             System.out.println("No sales history file found. Starting fresh.");
        } catch (Exception e) {
            System.err.println("Error loading sales history: " + e.getMessage());
        }
    }
    private void saveSalesHistoryToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SALES_FILE))) {
            oos.writeObject(restaurant.getSalesHistory());
        } catch (Exception e) {
            System.err.println("Error saving sales history: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new RestaurantManagementSystem();
            }
        });
    }
}