package ru.itmo.gorshkov;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import javafx.util.converter.DoubleStringConverter;
import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

import java.util.Comparator;
import java.util.function.Function;

public class MainController {
    public static final String sin = "sin(x)";
    public static final String lnSin = "ln(x-5)*sin(x)+2";
    public static final String square = "x^2";

    public TableView<Node> table;
    public TableColumn<Node, Double> table_x;
    public TableColumn<Node, Double> table_y;

    public Text error;
    public AnchorPane chartBox;
    public ComboBox<String> comboBox;
    public TextField begin;
    public TextField end;
    public TextField partition;
    public Text error_nodes;
    public TextField equation;
    public TextField add_x;
    public Text find_y;
    public TextField find_y_field;
    public Button find_y_button;

    private Expression exp;
    private double a;
    private double b;
    private double[] s;

    @FXML
    private void initialize() {
        find_y_field.setDisable(true);
        find_y_button.setDisable(true);
        table.setEditable(true);
        table_x.setCellValueFactory(new PropertyValueFactory<>("x"));
        table_x.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        table_y.setCellValueFactory(new PropertyValueFactory<>("y"));
        table_y.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        table_x.setOnEditCommit(nodeDoubleCellEditEvent -> {
            TablePosition<Node, Double> pos = nodeDoubleCellEditEvent.getTablePosition();
            Double newX = nodeDoubleCellEditEvent.getNewValue();
            int row = pos.getRow();
            Node node = nodeDoubleCellEditEvent.getTableView().getItems().get(row);
            node.setX(newX);
            var e = getFunction();
            e.setArgumentValue("x", newX);
            var y = e.calculate();
            node.setY(y);
            table.refresh();
        });
        table_y.setOnEditCommit(nodeDoubleCellEditEvent -> {
            TablePosition<Node, Double> pos = nodeDoubleCellEditEvent.getTablePosition();
            Double newY = nodeDoubleCellEditEvent.getNewValue();
            int row = pos.getRow();
            Node node = nodeDoubleCellEditEvent.getTableView().getItems().get(row);
            node.setY(newY);
        });
        table.setPlaceholder(new Label(""));
        table_x.maxWidthProperty().bind(table.widthProperty().multiply(.50));
        table_x.minWidthProperty().bind(table_x.maxWidthProperty());

        ObservableList<String> func = FXCollections.observableArrayList("", sin, lnSin, square);
        comboBox.setItems(func);
        comboBox.setOnAction(actionEvent -> {
            table.getItems().removeAll(table.getItems());
            if (comboBox.getValue().equals("")) {
                equation.setDisable(false);
            } else {
                equation.setText("");
                equation.setDisable(true);
            }
        });
    }

    public void solve() {
        error_nodes.setVisible(false);
        var nodes = table.getItems();
        if (nodes.isEmpty())
            error_nodes.setVisible(true);
        else {
            nodes.sort(Comparator.comparingDouble(Node::getX));
            var compMethod = new SplineInterpolation(nodes, exp);
            s = compMethod.calcSpline();
            ChartPainter.drawChart(nodes.get(0).getX(), nodes.get(nodes.size() - 1).getX(), nodes, exp, s, chartBox);
        }
        find_y_field.setDisable(false);
        find_y_button.setDisable(false);
    }

    @FXML
    public void feelNodes() {
        find_y_field.setDisable(true);
        find_y_button.setDisable(true);
        find_y.setVisible(false);
        try {
            error.setVisible(false);
            a = parseDouble(begin);
            b = parseDouble(end);
            var n = Integer.parseInt(partition.getText());
            exp = getFunction();
            var nodes = MathUtil.calcNode(a, b, n, exp);
            table.setItems(FXCollections.observableList(nodes));
        } catch (Exception e) {
            error.setVisible(true);
            begin.clear();
            end.clear();
            partition.clear();
        }
    }

    @FXML
    public void addNode() {
        try {
            error.setVisible(false);
            double x = parseDouble(add_x);
            exp = getFunction();
            exp.setArgumentValue("x", x);
            double y = exp.calculate();
            table.getItems().add(new Node(x, y));
            table.refresh();
            add_x.clear();
        } catch (Exception e) {
            error.setVisible(true);
            add_x.clear();
        }
    }

    @FXML
    public void findY() {
        var nodes = table.getItems();
        var X = Double.parseDouble(find_y_field.getText());
        int n = nodes.size() - 1;
        for (int i = 1; i <= n; i += 1) {
            int finalI = i;
            double y = Double.NaN;
            if (X >= nodes.get(i - 1).getX() && X <= nodes.get(i).getX()) {
                Function<Double, Double> func = x -> {
                    double x0 = nodes.get(finalI - 1).getX();
                    double y0 = nodes.get(finalI - 1).getY();
                    double x1 = nodes.get(finalI).getX();
                    double y1 = nodes.get(finalI).getY();
                    double h = x1 - x0;
                    return y0 * Math.pow(x - x1, 2) * (2 * (x - x0) + h) / Math.pow(h, 3) +
                            s[finalI - 1] * Math.pow(x - x1, 2) * (x - x0) / Math.pow(h, 2) +
                            y1 * Math.pow(x - x0, 2) * (2 * (x1 - x) + h) / Math.pow(h, 3) +
                            s[finalI] * Math.pow(x - x0, 2) * (x - x1) / Math.pow(h, 2);
                };
                y = func.apply(X);
            }
            if (Double.isFinite(y)) {
                find_y.setText(Double.toString(y));
                find_y.setVisible(true);
            }
        }
    }

    public Expression getFunction() {
        String func;
        if (comboBox.getValue() == null || comboBox.getValue().equals("")) {
            func = equation.getText();
        } else {
            func = comboBox.getValue();
        }
        var e = new Expression(func);
        e.addArguments(new Argument("x", 0));
        if (e.checkSyntax())
            return e;
        else throw new IllegalArgumentException();
    }

    private double parseDouble(TextField text) {
        return Double.parseDouble(text.getText());
    }

    @FXML
    public void test1() {
        begin.setText("10");
        end.setText("20");
        partition.setText("10");
        comboBox.setValue(sin);
    }

    @FXML
    public void test2() {
        begin.setText("-10");
        end.setText("10");
        partition.setText("50");
        equation.setText("sin(x)*x^3-cos(x)");
    }
}
