//package com.driver.services.impl;
//
//import com.driver.model.*;
//import com.driver.repository.ConnectionRepository;
//import com.driver.repository.ServiceProviderRepository;
//import com.driver.repository.UserRepository;
//import com.driver.services.ConnectionService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//public class ConnectionServiceImpl implements ConnectionService {
//    @Autowired
//    UserRepository userRepository2;
//    @Autowired
//    ServiceProviderRepository serviceProviderRepository2;
//    @Autowired
//    ConnectionRepository connectionRepository2;
//
//    @Override
//    public User connect(int userId, String countryName) throws Exception{
//
//    }
//    @Override
//    public User disconnect(int userId) throws Exception {
//
//    }
//    @Override
//    public User communicate(int senderId, int receiverId) throws Exception {
//
//    }
//}
package com.driver.services.impl;

import com.driver.model.*;
import com.driver.repository.ConnectionRepository;
import com.driver.repository.ServiceProviderRepository;
import com.driver.repository.UserRepository;
import com.driver.services.ConnectionService;
import com.driver.services.Transformer.CountryTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConnectionServiceImpl implements ConnectionService {
    @Autowired
    UserRepository userRepository2;
    @Autowired
    ServiceProviderRepository serviceProviderRepository2;
    @Autowired
    ConnectionRepository connectionRepository2;

    @Override
    public User connect(int userId, String countryName) throws Exception{
        //Connect the user to a vpn by considering the following priority order.
        //1. If the user is already connected to any service provider, throw "Already connected" exception.
        //2. Else if the countryName corresponds to the original country of the user, do nothing.
        // This means that the user wants to connect to its original country, for which we do not require a connection.
        // Thus, return the user as it is.
        //3. Else, the user should be subscribed under a serviceProvider having option to connect to the given country.
        //If the connection can not be made (As user does not have a serviceProvider or serviceProvider does not have given country,
        // throw "Unable to connect" exception.
        //Else, establish the connection where the maskedIp is "updatedCountryCode.serviceProviderId.userId" and return the updated user.
        // If multiple service providers allow you to connect to the country, use the service provider having smallest id.
        User user = userRepository2.findById(userId).get();
        if(user == null){
            throw new Exception("no user present");
        }

        if(user.getConnected()==true){
            throw new Exception("Already connected");
        }

        String upper_countryName = countryName.toUpperCase();
        CountryName countryToConnect;
        try{
            countryToConnect = CountryName.valueOf(upper_countryName);
        } catch (Exception e){
            throw new Exception("Country not found");
        }

        if(user.getOriginalCountry().getCountryName().equals(countryToConnect)){
            return user;
        }

        List<ServiceProvider> serviceProviders = user.getServiceProviderList();
        boolean flag = false;
        int serviceProviderId = Integer.MAX_VALUE;
        ServiceProvider serviceProviderToConnect = null;
        for(ServiceProvider serviceProvider : serviceProviders){
            for(Country country : serviceProvider.getCountryList()){
                if(country.getCountryName().equals(countryToConnect)){
                    flag = true;
                    if(serviceProviderId>serviceProvider.getId()){
                        serviceProviderId = serviceProvider.getId();
                        serviceProviderToConnect = serviceProvider;
                    }
                }
            }
        }
        if(flag == false){
            throw new Exception("Unable to connect");
        }

        Connection connection = new Connection();
        connection.setServiceProvider(serviceProviderToConnect);
        connection.setUser(user);
//        Connection savedConnection = connectionRepository2.save(connection);

        user.getConnectionList().add(connection);
        user.setConnected(true);
        user.setMaskedIp("" + countryToConnect.toCode() + "." + serviceProviderId + "." + user.getId());
        User saveduser = userRepository2.save(user);
        int size = user.getConnectionList().size();
        Connection savedConnection = user.getConnectionList().get(size-1);
        serviceProviderToConnect.getConnectionList().add(savedConnection);
        ServiceProvider savedServiceProvider = serviceProviderRepository2.save(serviceProviderToConnect);
        return user;
    }
    @Override
    public User disconnect(int userId) throws Exception {
        //If the given user was not connected to a vpn, throw "Already disconnected" exception.
        //Else, disconnect from vpn, make masked Ip as null, update relevant attributes and return updated user.
        User user = userRepository2.findById(userId).get();
        if(!user.getConnected()|| user.getConnected()==null)
            throw new Exception("Already disconnected");
        user.setMaskedIp(null);
        user.setConnected(false);
        userRepository2.save(user);
        return user;
    }
    @Override
    public User communicate(int senderId, int receiverId) throws Exception {
        //Establish a connection between sender and receiver users
        //To communicate to the receiver, sender should be in the current country of the receiver.
        //If the receiver is connected to a vpn, his current country is the one he is connected to.
        //If the receiver is not connected to vpn, his current country is his original country.
        //The sender is initially not connected to any vpn. If the sender's original country does not match receiver's current country,
        // we need to connect the sender to a suitable vpn. If there are multiple options, connect using the service provider having smallest id
        //If the sender's original country matches receiver's current country, we do not need to do anything as they can communicate.
        // Return the sender as it is.
        //If communication can not be established due to any reason, throw "Cannot establish communication" exception
        User sender = userRepository2.findById(senderId).get();
        if(sender == null){
            throw new Exception("Sender not present");
        }
        User receiver = userRepository2.findById(receiverId).get();
        if(receiver == null){
            throw new Exception("Receiver not present");
        }

        CountryName countryNameofReceiver;
        if(receiver.getConnected()==true){
            countryNameofReceiver = CountryTransformer.getCountryByCode(receiver.getMaskedIp().substring(0,3));
        }
        else {
            countryNameofReceiver = receiver.getOriginalCountry().getCountryName();
        }

        CountryName countryNameOfSender = sender.getOriginalCountry().getCountryName();

        if(countryNameofReceiver.equals(countryNameOfSender)){
            return sender;
        }

        List<ServiceProvider> serviceProviders = sender.getServiceProviderList();
        boolean flag = false;
        int serviceProviderId = Integer.MAX_VALUE;
        ServiceProvider serviceProviderToConnect = null;
        for(ServiceProvider serviceProvider : serviceProviders){
            for(Country country : serviceProvider.getCountryList()){
                if(country.getCountryName().equals(countryNameofReceiver)){
                    flag = true;
                    if(serviceProviderId > serviceProvider.getId()){
                        serviceProviderId = serviceProvider.getId();
                        serviceProviderToConnect = serviceProvider;
                    }
                }
            }
        }
        if(flag == false){
            throw new Exception("Cannot establish communication");
        }

        Connection connection = new Connection();
        connection.setServiceProvider(serviceProviderToConnect);
        connection.setUser(sender);
        Connection savedConnection = connectionRepository2.save(connection);

        serviceProviderToConnect.getConnectionList().add(savedConnection);
        sender.getConnectionList().add(savedConnection);
        sender.setConnected(true);
        sender.setMaskedIp("" + countryNameofReceiver.toCode() + "." + serviceProviderId + "." + sender.getId() );
        ServiceProvider savedServiceProvider = serviceProviderRepository2.save(serviceProviderToConnect);
        User saveduser = userRepository2.save(sender);
        return saveduser;

    }
}