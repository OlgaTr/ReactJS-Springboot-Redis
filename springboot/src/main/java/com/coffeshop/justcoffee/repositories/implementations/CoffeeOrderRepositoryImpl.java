package com.coffeshop.justcoffee.repositories.implementations;

import com.coffeshop.justcoffee.models.Coffee;
import com.coffeshop.justcoffee.models.CoffeeOrder;
import com.coffeshop.justcoffee.models.Topping;
import com.coffeshop.justcoffee.repositories.interfaces.CoffeeOrderRepository;
import com.coffeshop.justcoffee.repositories.interfaces.CoffeeRepository;
import com.coffeshop.justcoffee.repositories.interfaces.ToppingRepository;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.coffeshop.justcoffee.utils.IdGenerator.generateId;

@Repository
public class CoffeeOrderRepositoryImpl implements CoffeeOrderRepository {
    private static final String KEY = "COFFEE_ORDER";
    private final RedisTemplate<String, CoffeeOrder> coffeeOrderTemplate;
    private final CoffeeRepository coffeeRepository;
    private final ToppingRepository toppingRepository;
    private HashOperations coffeeOrderHashOperations;

    public CoffeeOrderRepositoryImpl(RedisTemplate<String, CoffeeOrder> orderTemplate, CoffeeRepository coffeeRepository, ToppingRepository toppingRepository) {
        this.coffeeOrderTemplate = orderTemplate;
        this.coffeeRepository = coffeeRepository;
        this.toppingRepository = toppingRepository;
    }

    @PostConstruct
    private void init() {
        coffeeOrderHashOperations = coffeeOrderTemplate.opsForHash();
    }

    @Override
    public Collection<CoffeeOrder> findAllCoffeeOrders() {
        return (Collection<CoffeeOrder>) coffeeOrderHashOperations.entries(KEY).values();
    }

    @Override
    public long createCoffeeOrder(long coffeeId, Long[] toppingsId) {
        CoffeeOrder coffeeOrder = new CoffeeOrder(coffeeId, Arrays.asList(toppingsId));
        coffeeOrder.setDescription(buildDescription(coffeeOrder));
        coffeeOrder.setPrice(calculatePrice(coffeeOrder));
        long generatedId = generateId();
        coffeeOrder.setId(generatedId);
        coffeeOrderHashOperations.put(KEY, generatedId, coffeeOrder);
        return generatedId;
    }

    @Override
    public CoffeeOrder getCoffeeOrderById(long coffeeOrderId) {
        return (CoffeeOrder) coffeeOrderHashOperations.get(KEY, coffeeOrderId);
    }

    @Override
    public List<CoffeeOrder> getCoffeeDrinksById(Long[] coffeeDrinksId) {
        return Arrays.stream(coffeeDrinksId)
                .map(id -> (CoffeeOrder) coffeeOrderHashOperations.get(KEY, id))
                .collect(Collectors.toList());
    }

    @Override
    public CoffeeOrder updateCoffeeOrder(CoffeeOrder coffeeOrder) {
        long coffeeOrderId = coffeeOrder.getId();
        coffeeOrderHashOperations.delete(KEY, coffeeOrderId);
        coffeeOrderHashOperations.put(KEY, coffeeOrderId, coffeeOrder);
        return null;
    }

    @Override
    public void deleteAll() {
        coffeeOrderHashOperations.keys(KEY).stream().forEach(k -> coffeeOrderHashOperations.delete(KEY, k));
    }

    @Override
    public void deleteById(long id) {
        coffeeOrderHashOperations.delete(KEY, id);
    }

    private List<Topping> getToppingsById(List<Long> toppingsId) {
        return toppingsId.stream()
                .map(toppingId -> toppingRepository.findToppingById(toppingId))
                .collect(Collectors.toList());
    }

    private String buildDescription(CoffeeOrder coffeeOrder) {
        Coffee coffee = coffeeRepository.findCoffee(coffeeOrder.getCoffeeId());
        List<Topping> toppings = getToppingsById(coffeeOrder.getToppingsId());
        StringBuilder description = new StringBuilder(coffee.type);
        if (toppings.size() > 0) {
            description.append(" with ");
            List<String> types = toppings.stream().map(Topping::getType).collect(Collectors.toList());
            description.append(String.join(", ", types));
        }
        return description.toString();
    }

    private double calculatePrice(CoffeeOrder coffeeOrder) {
        Coffee coffee = coffeeRepository.findCoffee(coffeeOrder.getCoffeeId());
        double coffeePrice = coffee.getPrice();
        List<Topping> toppings = getToppingsById(coffeeOrder.getToppingsId());
        double toppingsPrice = toppings.stream().map(Topping::getPrice).mapToDouble(price -> price).sum();
        return (double) Math.round((coffeePrice + toppingsPrice)*100)/100;
    }
}
